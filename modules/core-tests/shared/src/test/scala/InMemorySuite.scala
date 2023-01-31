// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.Applicative
import cats.syntax.all._
import cats.data.Kleisli
import cats.effect.{IO, IOLocal, MonadCancelThrow}
import cats.mtl.Local
import munit.CatsEffectSuite
import natchez.InMemory.Lineage.defaultRootName
import natchez.mtl._

trait InMemorySuite extends CatsEffectSuite {
  type Lineage = InMemory.Lineage
  val Lineage = InMemory.Lineage
  type NatchezCommand = InMemory.NatchezCommand
  val NatchezCommand = InMemory.NatchezCommand
}

object InMemorySuite {
  trait TraceSuite extends InMemorySuite {
    trait TraceTest {
      def program[F[_]: MonadCancelThrow: Trace]: F[Unit]

      def expectedHistory: List[(Lineage, NatchezCommand)]
    }

    def traceTest(name: String, tt: TraceTest): Unit = {
      test(s"$name - Kleisli")(
        testTraceKleisli(tt.program[Kleisli[IO, Span[IO], *]](implicitly, _), tt.expectedHistory)
      )
      test(s"$name - IOLocal")(testTraceIoLocal(tt.program[IO](implicitly, _), tt.expectedHistory))
    }

    def testTraceKleisli(
        traceProgram: Trace[Kleisli[IO, Span[IO], *]] => Kleisli[IO, Span[IO], Unit],
        expectedHistory: List[(Lineage, NatchezCommand)]
    ): IO[Unit] = testTrace[Kleisli[IO, Span[IO], *]](
      traceProgram,
      root => IO.pure(Trace[Kleisli[IO, Span[IO], *]] -> (k => k.run(root))),
      expectedHistory
    )

    def testTraceIoLocal(
        traceProgram: Trace[IO] => IO[Unit],
        expectedHistory: List[(Lineage, NatchezCommand)]
    ): IO[Unit] = testTrace[IO](traceProgram, Trace.ioTrace(_).map(_ -> identity), expectedHistory)

    def testTrace[F[_]](
        traceProgram: Trace[F] => F[Unit],
        makeTraceAndResolver: Span[IO] => IO[(Trace[F], F[Unit] => IO[Unit])],
        expectedHistory: List[(Lineage, NatchezCommand)]
    ): IO[Unit] =
      InMemory.EntryPoint.create[IO].flatMap { ep =>
        val traced = ep.root(defaultRootName).use { r =>
          makeTraceAndResolver(r).flatMap { case (traceInstance, resolve) =>
            resolve(traceProgram(traceInstance))
          }
        }
        traced *> ep.ref.get.map { history =>
          assertEquals(history.toList, expectedHistory)
        }
      }
  }

  trait LocalSuite extends InMemorySuite {
    trait LocalTest {
      def program[F[_]: MonadCancelThrow](implicit L: Local[F, Span[F]]): F[Unit]
      def expectedHistory: List[(Lineage, NatchezCommand)]
    }

    def localTest(name: String, tt: LocalTest): Unit = {
      test(s"$name - Kleisli")(
        testLocalKleisli(tt.program[Kleisli[IO, Span[IO], *]](implicitly, _), tt.expectedHistory)
      )
      test(s"$name - IOLocal")(testLocalIoLocal(tt.program[IO](implicitly, _), tt.expectedHistory))
    }

    def testLocalKleisli(
        localProgram: Local[Kleisli[IO, Span[IO], *], Span[Kleisli[IO, Span[IO], *]]] => Kleisli[
          IO,
          Span[IO],
          Unit
        ],
        expectedHistory: List[(Lineage, NatchezCommand)]
    ): IO[Unit] = testTraceViaLocal[Kleisli[IO, Span[IO], *]](
      localProgram,
      root =>
        IO.pure(
          implicitly[Local[Kleisli[IO, Span[IO], *], Span[Kleisli[IO, Span[IO], *]]]] -> (k =>
            k.run(root)
          )
        ),
      expectedHistory
    )

    def testLocalIoLocal(
        localProgram: Local[IO, Span[IO]] => IO[Unit],
        expectedHistory: List[(Lineage, NatchezCommand)]
    ): IO[Unit] =
      testTraceViaLocal[IO](
        localProgram,
        IOLocal(_)
          .map { ioLocal =>
            new Local[IO, Span[IO]] {
              override def local[A](fa: IO[A])(f: Span[IO] => Span[IO]): IO[A] =
                ioLocal.get.flatMap { initial =>
                  ioLocal.set(f(initial)) >> fa.guarantee(ioLocal.set(initial))
                }

              override def applicative: Applicative[IO] = implicitly

              override def ask[E2 >: Span[IO]]: IO[E2] = ioLocal.get
            }
          }
          .tupleRight(identity[IO[Unit]]),
        expectedHistory
      )

    def testTraceViaLocal[F[_]](
        localProgram: Local[F, Span[F]] => F[Unit],
        makeTraceAndResolver: Span[IO] => IO[(Local[F, Span[F]], F[Unit] => IO[Unit])],
        expectedHistory: List[(Lineage, NatchezCommand)]
    ): IO[Unit] =
      InMemory.EntryPoint.create[IO].flatMap { ep =>
        val traced = ep.root(defaultRootName).use { r =>
          makeTraceAndResolver(r).flatMap { case (localInstance, resolve) =>
            resolve(localProgram(localInstance))
          }
        }
        traced *> ep.ref.get.map { history =>
          assertEquals(history.toList, expectedHistory)
        }
      }
  }

}
