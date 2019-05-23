// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.effect.Resource
import cats.effect.Sync

trait Tracer[F[_]] {
  def root(label: String): Resource[F, Span[F]]
}

object Tracer {

  def fromOpenTracing[F[_]: Sync](
    otTracer: io.opentracing.Tracer
  ): Tracer[F] =
    new Tracer[F] {

      def root(label: String): Resource[F, Span[F]] =
        Resource.make(
          Sync[F].delay(otTracer.buildSpan(label).start()))(
          s => Sync[F].delay(s.finish())
        ).map(Span.fromOpenTracing(otTracer, _))

    }

}