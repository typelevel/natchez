// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats._
import cats.data.Kleisli
import cats.effect.{Trace => _, _}
import cats.mtl.{Local, MonadPartialOrder}

package object mtl {
  implicit def natchezMtlTraceForLocal[F[_]](implicit
      ev: Local[F, Span[F]],
      eb: MonadCancel[F, Throwable]
  ): Trace[F] =
    new LocalTrace(ev)

  implicit def localSpanForKleisli[F[_]](implicit
      F: MonadCancel[F, _]
  ): Local[Kleisli[F, Span[F], *], Span[Kleisli[F, Span[F], *]]] =
    new Local[Kleisli[F, Span[F], *], Span[Kleisli[F, Span[F], *]]] {
      override def local[A](
          fa: Kleisli[F, Span[F], A]
      )(f: Span[Kleisli[F, Span[F], *]] => Span[Kleisli[F, Span[F], *]]): Kleisli[F, Span[F], A] =
        fa.local {
          f.andThen(_.mapK(Kleisli.applyK(Span.noop[F])))
            .compose(_.mapK(MonadPartialOrder[F, Kleisli[F, Span[F], *]]))
        }

      override def applicative: Applicative[Kleisli[F, Span[F], *]] =
        MonadPartialOrder[F, Kleisli[F, Span[F], *]].monadG

      override def ask[E2 >: Span[Kleisli[F, Span[F], *]]]: Kleisli[F, Span[F], E2] =
        Kleisli.ask[F, Span[F]].map(_.mapK(MonadPartialOrder[F, Kleisli[F, Span[F], *]]))
    }
}
