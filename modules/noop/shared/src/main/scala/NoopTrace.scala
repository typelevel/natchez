// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package noop

import cats.~>
import cats.Applicative
import cats.arrow.FunctionK
import cats.effect.Resource
import cats.syntax.all._
import java.net.URI

final case class NoopTrace[F[_]: Applicative]() extends Trace[F] {

  override def put(fields: (String, TraceValue)*): F[Unit] =
    Applicative[F].unit

  override def attachError(err: Throwable, fields: (String, TraceValue)*): F[Unit] =
    Applicative[F].unit

  override def kernel: F[Kernel] =
    Applicative[F].pure(Kernel(Map.empty))

  override def log(fields: (String, TraceValue)*): F[Unit] = Applicative[F].unit

  override def log(event: String): F[Unit] = Applicative[F].unit

  override def spanR(name: String, options: Span.Options): Resource[F, F ~> F] =
    Resource.pure(FunctionK.id)

  override def span[A](name: String, options: Span.Options)(k: F[A]): F[A] =
    k

  override def traceId: F[Option[String]] =
    none.pure[F]

  override def spanId(implicit A: Applicative[F]): F[Option[String]] =
    A.pure(None)

  override def traceUri: F[Option[URI]] =
    none.pure[F]
}
