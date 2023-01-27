// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package mtl

import cats._
import cats.data._
import cats.effect.testkit.TestInstances
import cats.effect.{Trace => _, _}
import cats.laws.discipline.MiniInt.allValues
import cats.laws.discipline.{ExhaustiveCheck, MiniInt}
import cats.laws.discipline.arbitrary._
import cats.laws.discipline.eq._
import cats.mtl.laws.discipline.LocalTests
import cats.syntax.all._
import munit.DisciplineSuite
import natchez.mtl.SeededSpan.{seedKey, spanToSeed}
import org.scalacheck.rng.Seed
import org.scalacheck.{Arbitrary, Cogen, Gen}
import org.typelevel.ci.CIStringSyntax

import java.net.URI
import scala.util.Try

class LocalInstancesSpec extends DisciplineSuite with TestInstances {
  implicit val ticker: Ticker = Ticker()

  private implicit def exhaustiveCheckSpan[F[_]: Applicative]: ExhaustiveCheck[Span[F]] =
    ExhaustiveCheck.instance(allValues.map(new SeededSpan[F](_)))

  private def genFromExhaustiveCheck[A: ExhaustiveCheck]: Gen[A] =
    Gen.oneOf(ExhaustiveCheck[A].allValues)

  private implicit def arbFromExhaustiveCheck[A: ExhaustiveCheck]: Arbitrary[A] =
    Arbitrary(genFromExhaustiveCheck)

  implicit def comonadIO: Comonad[IO] = new Comonad[IO] {
    override def extract[A](x: IO[A]): A =
      unsafeRun(x).fold(
        throw new RuntimeException("canceled"),
        throw _,
        _.get
      )

    override def coflatMap[A, B](fa: IO[A])(f: IO[A] => B): IO[B] =
      f(fa).pure[IO]

    override def map[A, B](fa: IO[A])(f: A => B): IO[B] = fa.map(f)
  }

  private implicit def cogenSpan[F[_]: Comonad]: Cogen[Span[F]] =
    Cogen(spanToSeed(_))

  private implicit def cogenSpanK[F[_]: Comonad](implicit
      F: MonadCancel[F, _]
  ): Cogen[Span[Kleisli[F, Span[F], *]]] =
    Cogen { (seed: Seed, span: Span[Kleisli[F, Span[F], *]]) =>
      seed.reseed(
        spanToSeed(
          span.mapK(
            Kleisli.applyK[F, Span[F]](
              genFromExhaustiveCheck[Span[F]].apply(Gen.Parameters.default, seed).get
            )
          )
        )
      )
    }

  private implicit def eqKleisli[F[_], A: ExhaustiveCheck, B](implicit
      ev: Eq[F[B]]
  ): Eq[Kleisli[F, A, B]] =
    Eq.by((x: Kleisli[F, A, B]) => x.run)

  private implicit def kernelEq: Eq[Kernel] = Eq.by(_.toHeaders)
  private implicit def spanKEq: Eq[Span[Kleisli[IO, Span[IO], *]]] = Eq.by(_.kernel)

  checkAll(
    "Local[Kleisli[F, Span[F], *], Span[Kleisli[F, Span[F], *]]]",
    LocalTests[Kleisli[IO, Span[IO], *], Span[Kleisli[IO, Span[IO], *]]](localSpanForKleisli)
      .local[Int, Int]
  )
}

object SeededSpan {
  val seedKey = ci"seed"

  def spanToSeed[F[_]: Comonad](span: Span[F]): Long =
    span.kernel.extract.toHeaders
      .get(seedKey)
      .flatMap { s =>
        Try(java.lang.Long.parseUnsignedLong(s, 16)).toOption
      }
      .get
}

private class SeededSpan[F[_]: Applicative](seed: MiniInt) extends Span[F] {
  override def put(fields: (String, TraceValue)*): F[Unit] = ().pure[F]
  override def log(fields: (String, TraceValue)*): F[Unit] = ().pure[F]
  override def log(event: String): F[Unit] = ().pure[F]
  override def attachError(err: Throwable, fields: (String, TraceValue)*): F[Unit] = ().pure[F]
  override def kernel: F[Kernel] = Kernel(Map(seedKey -> seed.toInt.toHexString)).pure[F]
  override def span(name: String, options: Span.Options): Resource[F, Span[F]] =
    Resource.pure(this)
  override def traceId: F[Option[String]] = Seed(seed.toInt.toLong).toBase64.some.pure[F]
  override def spanId: F[Option[String]] = Seed(seed.toInt.toLong).toBase64.some.pure[F]
  override def traceUri: F[Option[URI]] = none[URI].pure[F]
}
