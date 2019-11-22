// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package opencensus

import cats.effect.{Resource, Sync}
import cats.implicits._
import io.opencensus.trace.propagation.TextFormat.Setter
import io.opencensus.trace.{AttributeValue, Tracer, Tracing}
import natchez.TraceValue.{BooleanValue, NumberValue, StringValue}

import scala.collection.mutable

private[opencensus] final case class OpenCensusSpan[F[_]: Sync](
    tracer: Tracer,
    span: io.opencensus.trace.Span)
    extends Span[F] {
  import OpenCensusSpan._

  override def put(fields: (String, TraceValue)*): F[Unit] =
    fields.toList.traverse_ {
      case (k, StringValue(v)) =>
        Sync[F].delay(
          span.putAttribute(k, AttributeValue.stringAttributeValue(v)))
      case (k, NumberValue(v)) =>
        Sync[F].delay(
          span.putAttribute(
            k,
            AttributeValue.doubleAttributeValue(v.doubleValue())))
      case (k, BooleanValue(v)) =>
        Sync[F].delay(
          span.putAttribute(k, AttributeValue.booleanAttributeValue(v)))
    }

  override def kernel: F[Kernel] = Sync[F].delay {
    val headers: mutable.Map[String, String] = mutable.Map.empty[String, String]
    Tracing.getPropagationComponent.getB3Format
      .inject(span.getContext, headers, spanContextSetter)
    Kernel.fromHeaders(headers.toMap)(OpenCensusHeaderKey)
  }

  override def span(name: String): Resource[F, Span[F]] =
    Resource
      .make(
        Sync[F].delay(
          tracer
            .spanBuilderWithExplicitParent(name, span)
            .startSpan()))(s => Sync[F].delay(s.end()))
      .map(OpenCensusSpan(tracer, _))
}

object OpenCensusSpan {
  private val spanContextSetter = new Setter[mutable.Map[String, String]] {
    override def put(carrier: mutable.Map[String, String],
                     key: String,
                     value: String): Unit = {
      carrier.put(key, value)
      ()
    }
  }
}
