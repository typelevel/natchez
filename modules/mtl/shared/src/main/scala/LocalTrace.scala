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

private[mtl] class LocalTrace[F[_]](local: Local[F, Span[F]])(implicit
    ev: MonadCancel[F, Throwable]
) extends Trace[F] {

  override def kernel: F[Kernel] =
    local.ask.flatMap(_.kernel)

  override def put(fields: (String, TraceValue)*): F[Unit] =
    local.ask.flatMap(_.put(fields: _*))

  override def attachError(err: Throwable): F[Unit] =
    local.ask.flatMap(_.attachError(err))

  override def log(fields: (String, TraceValue)*): F[Unit] =
    local.ask.flatMap(_.log(fields: _*))

  override def log(event: String): F[Unit] =
    local.ask.flatMap(_.log(event))

  override def spanR(name: String, options: Span.Options): Resource[F, F ~> F] =
    Resource(
      local.ask.flatMap(t =>
        t.span(name, options).allocated.map { case (child, release) =>
          new (F ~> F) {
            def apply[A](fa: F[A]): F[A] =
              local.scope(fa)(child)
          } -> release
        }
      )
    )

  override def span[A](name: String, options: Span.Options)(k: F[A]): F[A] =
    local.ask.flatMap { span =>
      span.span(name, options).use { s =>
        local.scope(k)(s).onError { case err => s.attachError(err) }
      }
    }

  override def traceId: F[Option[String]] =
    local.ask.flatMap(_.traceId)

  override def traceUri: F[Option[URI]] =
    local.ask.flatMap(_.traceUri)
}
