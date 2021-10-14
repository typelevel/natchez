// Copyright (c) 2019-2021 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package noop

import cats._
import cats.effect.Resource
import cats.syntax.all._
import java.net.URI

final case class NoopSpan[F[_]: Applicative]() extends Span[F] {

  override def put(fields: (String, TraceValue)*): F[Unit] =
    Applicative[F].unit

  override def kernel: F[Kernel] =
    Applicative[F].pure(Kernel(Map.empty))

  override def span(name: String): Resource[F, Span[F]] =
    Resource.eval(NoopSpan[F]().pure[F])

  // TODO
  def traceId: F[Option[String]] = none.pure[F]

  def spanId: F[Option[String]] = none.pure[F]

  def traceUri: F[Option[URI]] = none.pure[F]

}
