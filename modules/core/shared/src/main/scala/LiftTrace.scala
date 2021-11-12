// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.data.Kleisli
import cats.effect.kernel.MonadCancelThrow
import cats.effect.IO

trait Traceable[A] {
  def run[F[_]](implicit trace: Trace[F]): F[A]
}

trait LiftTrace[F[_]] {
  def lift[A](span: Span[F], traceable: Traceable[A]): F[A]
}

object LiftTrace extends LiftTraceLowPriority {
  implicit def ioInstance: LiftTrace[IO] = new LiftTrace[IO] {
    def lift[A](span: Span[IO], traceable: Traceable[A]): IO[A] =
      Trace.ioTrace(span).flatMap(traceable.run(_))
  }
}

trait LiftTraceLowPriority {
  implicit def kleisliInstance[F[_]: MonadCancelThrow]: LiftTrace[F] = new LiftTrace[F] {
    def lift[A](span: Span[F], traceable: Traceable[A]): F[A] = traceable.run[Kleisli[F, Span[F], *]].apply(span)
  }
}
