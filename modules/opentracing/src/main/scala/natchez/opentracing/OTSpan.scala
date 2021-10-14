// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez.opentracing

import cats.effect.Resource.ExitCase
import cats.effect.kernel.Resource
import cats.effect.kernel.Sync
import cats.syntax.all._
import io.opentracing
import io.opentracing.propagation.Format
import io.opentracing.propagation.TextMapAdapter
import io.{opentracing => ot}
import natchez.Kernel
import natchez.Span
import natchez.TraceValue
import natchez.TraceValue.BooleanValue
import natchez.TraceValue.NumberValue
import natchez.TraceValue.StringValue

import java.net.URI
import scala.jdk.CollectionConverters._

private[opentracing] final case class OTSpan[F[_]: Sync](
  tracer: ot.Tracer,
  span: ot.Span,
  mkUri: Option[MakeSpanUri],
) extends Span[F] {

  def kernel: F[Kernel] = Sync[F].delay {
    val m = new java.util.HashMap[String, String]
    tracer.inject(
      span.context,
      Format.Builtin.HTTP_HEADERS,
      new TextMapAdapter(m),
    )
    Kernel(m.asScala.toMap)
  }

  def put(fields: (String, TraceValue)*): F[Unit] = fields.toList.traverse_ {
    case (str, StringValue(value)) =>
      Sync[F].delay(span.setTag(str, value))
    case (str, NumberValue(value)) =>
      Sync[F].delay(span.setTag(str, value))
    case (str, BooleanValue(value)) =>
      Sync[F].delay(span.setTag(str, value))
  }

  def span(name: String): Resource[F, Span[F]] = OTSpan.make[F](name, Some(span.context()), tracer, mkUri)

  def traceId: F[Option[String]] = Sync[F].pure {
    val rawId = span.context.toTraceId
    if (rawId.nonEmpty)
      rawId.some
    else
      none
  }

  def spanId: F[Option[String]] = Sync[F].pure {
    val rawId = span.context.toSpanId
    if (rawId.nonEmpty)
      rawId.some
    else
      none
  }

  def traceUri: F[Option[URI]] =
    mkUri
      .map(_.makeUri _)
      .pure[F]
      .nested
      .ap2(traceId.nested, spanId.nested)
      .value

}

object OTSpan {
  private def asChildOf(child: ot.Tracer.SpanBuilder, parent: Option[ot.SpanContext]): ot.Tracer.SpanBuilder =
    parent.foldLeft(child)(_.asChildOf(_))

  def make[F[_]: Sync](name: String, parentOpt: Option[ot.SpanContext], tracer: ot.Tracer, mkUri: Option[MakeSpanUri]): Resource[F, Span[F]] = Span.putErrorFields(
    Resource
      .makeCase(Sync[F].delay(asChildOf(tracer.buildSpan(name), parentOpt).start())) {
        case (span, ExitCase.Errored(e)) =>
          Sync[F].delay(
            span
              .setTag(opentracing.tag.Tags.ERROR.getKey(), true)
              .log(
                Map(
                  opentracing.log.Fields.EVENT -> "error",
                  opentracing.log.Fields.MESSAGE -> e.getMessage(),
                ).asJava
              )
              .finish()
          )
        case (span, _) =>
          Sync[F].delay(span.finish())
      }
      .map(OTSpan(tracer, _, mkUri))
  )
}
