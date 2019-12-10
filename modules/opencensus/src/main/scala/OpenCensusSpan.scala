// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package opencensus

import cats.effect._
import cats.effect.ExitCase.Canceled
import cats.effect.ExitCase.Completed
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
    Kernel(headers.toMap)
  }

  override def span(name: String): Resource[F, Span[F]] =
    Resource
      .makeCase(OpenCensusSpan.child(this, name))(OpenCensusSpan.finish).widen
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

  def child[F[_]: Sync](
    parent: OpenCensusSpan[F],
    name:   String
  ): F[OpenCensusSpan[F]] =
    Sync[F].delay(
      parent
        .tracer
        .spanBuilderWithExplicitParent(name, parent.span)
        .startSpan()
    ).map(OpenCensusSpan(parent.tracer, _))

  def finish[F[_]: Sync]: (OpenCensusSpan[F], ExitCase[Throwable]) => F[Unit] = { (outer, exitCase) =>
    for {
      // collect error details, if any
      _  <- exitCase.some.collect {
              case ExitCase.Error(t: Fields) => t.fields.toList
            }.traverse(outer.put)
      _  <- Sync[F].delay {
              exitCase match {
                case Completed          => outer.span.setStatus(io.opencensus.trace.Status.OK)
                case Canceled           => outer.span.setStatus(io.opencensus.trace.Status.CANCELLED)
                case ExitCase.Error(ex) =>
                  outer.span.putAttribute("error.stack", AttributeValue.stringAttributeValue(ex.getMessage))
                  outer.span.putAttribute("error.msg",   AttributeValue.stringAttributeValue(ex.getStackTrace.mkString("\n")))

                  outer.span.setStatus(io.opencensus.trace.Status.INTERNAL.withDescription(ex.getMessage))
              }
            }
      _  <- Sync[F].delay(outer.span.end())
    } yield ()
  }
}
