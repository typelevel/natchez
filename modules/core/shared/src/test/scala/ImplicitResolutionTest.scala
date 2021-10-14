// Copyright (c) 2019-2021 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.data._
import cats.effect.MonadCancel

object ImplicitResolutionTest {

  // these should compile
  def kleisliTrace[F[_]](implicit ev: MonadCancel[F, Throwable]) =
    Trace[Kleisli[F, Span[F], *]]

  def kleisliLiftedTrace[F[_]: Trace] =
    Trace[Kleisli[F, Int, *]]

  def kleisliKleisliTrace[F[_]](implicit ev: MonadCancel[F, Throwable]) =
    Trace[Kleisli[Kleisli[F, Span[F], *], Int, *]]

  def kleisliKleisliTrace2[F[_]](implicit ev: MonadCancel[F, Throwable]) =
    Trace[Kleisli[Kleisli[F, Int, *], Span[Kleisli[F, Int, *]], *]]
}