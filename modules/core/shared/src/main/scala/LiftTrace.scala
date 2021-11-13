// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.data.Kleisli

trait LiftTrace[F[_], G[_]] {
  def lift[A](span: Span[F], ga: G[A]): F[A]
}

object LiftTrace {
  implicit def kleisliInstance[F[_]]: LiftTrace[F, Kleisli[F, Span[F], *]] =
    new LiftTrace[F, Kleisli[F, Span[F], *]] {
      def lift[A](span: Span[F], ga: Kleisli[F, Span[F], A]): F[A] = ga(span)
    }
}
