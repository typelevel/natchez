// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package jaeger

import io.{ opentracing => ot }
import cats.effect.Sync
import cats.effect.Resource
import cats.implicits._
import io.opentracing.propagation.Format
import io.opentracing.propagation.TextMapAdapter

import scala.jdk.CollectionConverters._

private[jaeger] final case class JaegerSpan[F[_]: Sync](
  tracer: ot.Tracer,
  span:   ot.Span
) extends Span[F] {
  import TraceValue._

  def kernel: F[Kernel] =
    Sync[F].delay {
      val m = new java.util.HashMap[String, String]
      tracer.inject(
        span.context,
        Format.Builtin.HTTP_HEADERS,
        new TextMapAdapter(m)
      )
      Kernel(m.asScala.toMap)
    }

  def put(fields: (String, TraceValue)*): F[Unit] =
    fields.toList.traverse_ {
      case (k, StringValue(v))  => Sync[F].delay(span.setTag(k, v))
      case (k, NumberValue(v))  => Sync[F].delay(span.setTag(k, v))
      case (k, BooleanValue(v)) => Sync[F].delay(span.setTag(k, v))
    }

  def span(name: String): Resource[F,Span[F]] =
    Resource.make(
      Sync[F].delay(tracer.buildSpan(name).asChildOf(span).start))(
      s => Sync[F].delay(s.finish)
    ).map(JaegerSpan(tracer, _))

}
