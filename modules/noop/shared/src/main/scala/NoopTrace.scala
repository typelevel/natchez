// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package noop

import cats.Applicative
import cats.syntax.all._
import java.net.URI

final case class NoopTrace[F[_]: Applicative]() extends Trace[F] {

  override def put(fields: (String, TraceValue)*): F[Unit] =
    Applicative[F].unit

  override def kernel: F[Kernel] =
    Applicative[F].pure(Kernel(Map.empty))

  override def span[A](name: String)(k: F[A]): F[A] =
    k

  def traceId: F[Option[String]] =
    none.pure[F]

  def traceUri: F[Option[URI]] =
    none.pure[F]

}
