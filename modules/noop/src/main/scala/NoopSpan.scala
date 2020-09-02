// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package noop

import cats.Applicative
import cats.effect.Resource

final case class NoopSpan[F[_]: Applicative]() extends Span[F] {

  override def put(fields: (String, TraceValue)*): F[Unit] = {
    Applicative[F].unit
  }

  override def kernel: F[Kernel] = {
    Applicative[F].pure(Kernel(Map.empty))
  }

  override def span(name: String): Resource[F, Span[F]] = {
    Resource.liftF(Applicative[F].pure(NoopSpan()))
  }
}