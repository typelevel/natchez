// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

package mtl

import cats.~>
import cats.Applicative
import cats.mtl.Local
import cats.effect.MonadCancel
import cats.effect.Resource
import cats.syntax.all._
import java.net.URI

private[mtl] class LocalTrace[F[_]](local: Local[F, Span[F]])(
  implicit ev: MonadCancel[F, Throwable]
) extends Trace[F] {

    def kernel: F[Kernel] =
      local.ask.flatMap(_.kernel)

    def put(fields: (String, TraceValue)*): F[Unit] =
      local.ask.flatMap(_.put(fields: _*))

    def spanR[A](name: String)(r: Resource[F, A]): Resource[F, A] =
      Resource.suspend(
        local.ask.flatMap(span =>
          Applicative[F].pure(
            span.span(name).flatMap(child =>
              r.mapK(
                new (F ~> F) {
                  def apply[B](fb: F[B]) = local.scope(fb)(child)
                }
              )
            )
          )
        )
      )

    def span[A](name: String)(k: F[A]): F[A] =
      local.ask.flatMap { span =>
        span.span(name).use(local.scope(k))
      }

    def traceId: F[Option[String]] =
      local.ask.flatMap(_.traceId)

    def traceUri: F[Option[URI]] =
      local.ask.flatMap(_.traceUri)
}
