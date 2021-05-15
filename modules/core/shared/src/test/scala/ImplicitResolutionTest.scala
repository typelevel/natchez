// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.data._
import cats.Functor
import cats.effect.Bracket
import natchez.Trace.Implicits.noop

object ImplicitResolutionTest {

  // these should compile
  def kleisliTrace[F[_]](implicit ev: Bracket[F, Throwable]) =
    Trace[Kleisli[F, Span[F], *]]

  def kleisliLiftedTrace[F[_]: Trace: Functor] =
    Trace[Kleisli[F, Int, *]]

  def kleisliKleisliTrace[F[_]](implicit ev: Bracket[F, Throwable]) =
    Trace[Kleisli[Kleisli[F, Span[F], *], Int, *]]

  def kleisliKleisliTrace2[F[_]](implicit ev: Bracket[F, Throwable]) =
    Trace[Kleisli[Kleisli[F, Int, *], Span[Kleisli[F, Int, *]], *]]
}
