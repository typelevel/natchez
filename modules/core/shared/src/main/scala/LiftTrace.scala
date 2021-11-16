// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.arrow.FunctionK
import cats.data.Kleisli
import cats.effect.IO
import cats.effect.kernel.MonadCancelThrow
import cats.~>

trait LiftTrace[F[_], G[_]] {
  def run[A](span: Span[F])(f: Trace[G] => G[A]): F[A]
  def liftK: F ~> G
}

object LiftTrace extends LiftTraceLowPriority {
  implicit val ioInstance: LiftTrace[IO, IO] = new LiftTrace[IO, IO] {
    def run[A](span: Span[IO])(f: Trace[IO] => IO[A]): IO[A] =
      Trace.ioTrace(span).flatMap(f)
    def liftK: IO ~> IO = FunctionK.id
  }
}

private[natchez] trait LiftTraceLowPriority {
  implicit def kleisliInstance[F[_]: MonadCancelThrow]: LiftTrace[F, Kleisli[F, Span[F], *]] =
    new LiftTrace[F, Kleisli[F, Span[F], *]] {
      def run[A](span: Span[F])(f: Trace[Kleisli[F, Span[F], *]] => Kleisli[F, Span[F], A]): F[A] =
        f(Trace.kleisliInstance).run(span)
      def liftK: F ~> Kleisli[F, Span[F], *] = Kleisli.liftK
    }
}
