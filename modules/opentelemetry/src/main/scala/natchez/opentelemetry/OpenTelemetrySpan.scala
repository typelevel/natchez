// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package opentelemetry

import cats.data.Nested
import io.opentelemetry.api.trace.{Span => OtelSpan, Tracer => OtelTracer}
import cats.effect._
import cats.implicits._
import io.opentelemetry.api.{OpenTelemetry => OtelOpenTelemetry}
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapSetter
import natchez.TraceValue._

import scala.collection.mutable
import java.net.URI

final case class OpenTelemetrySpan[F[_] : Sync](sdk: OtelOpenTelemetry, tracer: OtelTracer, span: OtelSpan, prefix: Option[URI]) extends Span[F] {
  override def put(fields: (String, TraceValue)*): F[Unit] =
    fields.toList.traverse_ {
      case (k, StringValue(v))                       => Sync[F].delay(span.setAttribute(k, v))
      // all integer types are cast up to Long, since that's all OpenTelemetry lets us use
      case (k, NumberValue(n: java.lang.Byte))       => Sync[F].delay(span.setAttribute(k, n.toLong))
      case (k, NumberValue(n: java.lang.Short))      => Sync[F].delay(span.setAttribute(k, n.toLong))
      case (k, NumberValue(n: java.lang.Integer))    => Sync[F].delay(span.setAttribute(k, n.toLong))
      case (k, NumberValue(n: java.lang.Long))       => Sync[F].delay(span.setAttribute(k, n))
      // and all float types are changed to Double
      case (k, NumberValue(n: java.lang.Float))      => Sync[F].delay(span.setAttribute(k, n.toDouble))
      case (k, NumberValue(n: java.lang.Double))     => Sync[F].delay(span.setAttribute(k, n))
      // anything which could be too big to put in a Long is converted to a String
      case (k, NumberValue(n: java.math.BigDecimal)) => Sync[F].delay(span.setAttribute(k, n.toString))
      case (k, NumberValue(n: java.math.BigInteger)) => Sync[F].delay(span.setAttribute(k, n.toString))
      case (k, NumberValue(n: BigDecimal))           => Sync[F].delay(span.setAttribute(k, n.toString))
      case (k, NumberValue(n: BigInt))               => Sync[F].delay(span.setAttribute(k, n.toString))
      // and any other Number can fall back to a Double
      case (k, NumberValue(v))                       => Sync[F].delay(span.setAttribute(k, v.doubleValue()))
      case (k, BooleanValue(v))                      => Sync[F].delay(span.setAttribute(k, v))
    }

  object MutableMapKeySetter extends TextMapSetter[mutable.HashMap[String, String]] {
    override def set(carrier: mutable.HashMap[String, String], key: String, value: String): Unit =
      carrier.put(key, value): Unit
  }

  override def kernel: F[Kernel] = Sync[F].delay {
    val m = new mutable.HashMap[String, String]
    sdk.getPropagators.getTextMapPropagator.inject(
      Context.current.`with`(span),
      m,
      MutableMapKeySetter
    )
    Kernel(m.toMap)
  }

  override def span(name: String): Resource[F, Span[F]] =
    Span.putErrorFields(
      Resource.make[F, OtelSpan](
        Sync[F].delay {
          tracer.spanBuilder(name).setParent(Context.current().`with`(span)).startSpan()
        }) { s =>
        Sync[F].delay {
          s.end()
        }
      }.map(OpenTelemetrySpan(sdk, tracer, _, prefix))
    )

  override def traceId: F[Option[String]] = Sync[F].delay {
    val rawId = span.getSpanContext.getTraceId
    if (rawId.nonEmpty) rawId.some else none
  }

  override def spanId: F[Option[String]] = Sync[F].delay {
    val rawId = span.getSpanContext.getSpanId
    if (rawId.nonEmpty) rawId.some else none
  }

  override def traceUri: F[Option[URI]] =
    (Nested(prefix.pure[F]), Nested(traceId)).mapN { (uri, id) =>
      uri.resolve(s"/trace/$id")
    }.value
}
