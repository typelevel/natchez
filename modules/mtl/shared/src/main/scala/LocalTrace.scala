// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

package mtl

import cats.~>
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

    def spanR(name: String): Resource[F, F ~> F] =
      Resource(local.ask.flatMap(_.span(name).allocated.map {
        case (child, release) =>
          new (F ~> F) {
            def apply[A](fa: F[A]): F[A] =
              local.scope(fa)(child)
          } -> release
      }))

    def span[A](name: String)(k: F[A]): F[A] =
      local.ask.flatMap { span =>
        span.span(name).use(local.scope(k))
      }

    def traceId: F[Option[String]] =
      local.ask.flatMap(_.traceId)

    def traceUri: F[Option[URI]] =
      local.ask.flatMap(_.traceUri)

  override def span[A](name: String, kernel: Kernel)(k: F[A]): F[A] =
    local.ask.flatMap { span =>
      span.span(name, kernel).use(local.scope(k))
    }
}
