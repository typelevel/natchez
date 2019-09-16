// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package lightstep

import cats.effect.{ Resource, Sync }
import cats.implicits._
import io.{ opentracing => ot }
import io.opentracing.propagation.{ Format, TextMapAdapter }

import scala.jdk.CollectionConverters._

private[lightstep] final case class LightstepSpan[F[_]: Sync](
  tracer: ot.Tracer,
  span: ot.Span
) extends Span[F] {
  
  import TraceValue._

  override def kernel: F[Kernel] =
    Sync[F].delay {
      val m = new java.util.HashMap[String, String]
      tracer.inject(span.context, Format.Builtin.HTTP_HEADERS, new TextMapAdapter(m))
      Kernel(m.asScala.toMap)
    }

  override def put(fields: (String, TraceValue)*): F[Unit] =
    fields.toList.traverse_ {
      case (k, StringValue(v))  => Sync[F].delay(span.setTag(k, v))
      case (k, NumberValue(v))  => Sync[F].delay(span.setTag(k, v))
      case (k, BooleanValue(v)) => Sync[F].delay(span.setTag(k, v))
    }

  override def span(name: String): Resource[F,Span[F]] =
    Resource
      .make(Sync[F].delay(tracer.buildSpan(name).asChildOf(span).start()))(s => Sync[F].delay(s.finish()))
      .map(LightstepSpan(tracer, _))
}
