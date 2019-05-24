// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.effect.Resource
import cats.effect.Sync
import io.opentracing.propagation.Format
import scala.collection.JavaConverters._
import io.opentracing.propagation.TextMapExtractAdapter

trait Tracer[F[_]] {
  def root(label: String): Resource[F, Span[F]]
  def fromHttpHeaders(label: String, httpHeaders: Map[String, String]): Resource[F, Span[F]]
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

      def fromHttpHeaders(label: String, headers: Map[String, String]): Resource[F, Span[F]] =
        Resource.make(
          Sync[F].delay {
            val p = otTracer.extract(Format.Builtin.HTTP_HEADERS, new TextMapExtractAdapter(headers.asJava))
            otTracer.buildSpan(label).asChildOf(p).start()
          }
        )(s => Sync[F].delay(s.finish())).map(Span.fromOpenTracing(otTracer, _))

    }

}
