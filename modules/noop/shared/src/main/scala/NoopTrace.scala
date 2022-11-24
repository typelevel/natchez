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

  def attachError(err: Throwable): F[Unit] = Applicative[F].unit

  override def kernel: F[Kernel] =
    Applicative[F].pure(Kernel(Map.empty))

  override def log(fields: (String, TraceValue)*): F[Unit] = Applicative[F].unit

  override def log(event: String): F[Unit] = Applicative[F].unit

  override def spanR(name: String, options: Span.Options): Resource[F, F ~> F] =
    Resource.pure(FunctionK.id)

  override def span[A](name: String)(k: F[A]): F[A] =
    k

  override def span[A](name: String, options: Span.Options)(k: F[A]): F[A] =
    k

  def traceId: F[Option[String]] =
    none.pure[F]

  def traceUri: F[Option[URI]] =
    none.pure[F]
}
