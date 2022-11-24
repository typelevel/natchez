// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package lightstep

import cats.effect.{Resource, Sync}
import cats.syntax.all._
import io.opentracing.log.Fields
import io.{opentracing => ot}
import io.opentracing.propagation.{Format, TextMapAdapter}
import io.opentracing.tag.Tags

import scala.jdk.CollectionConverters._
import java.net.URI

private[lightstep] final case class LightstepSpan[F[_]: Sync](
    tracer: ot.Tracer,
    span: ot.Span,
    spanCreationPolicy: Span.Options.SpanCreationPolicy
) extends Span.Default[F] {

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

  override def attachError(err: Throwable): F[Unit] =
    put(
      Tags.ERROR.getKey -> true
    ) >>
      Sync[F].delay {
        span.log(
          Map(
            Fields.EVENT -> "error",
            Fields.ERROR_OBJECT -> err,
            Fields.ERROR_KIND -> err.getClass.getSimpleName,
            Fields.MESSAGE -> err.getMessage,
            Fields.STACK -> err.getStackTrace.mkString
          ).asJava
        )
      }.void

  override def log(event: String): F[Unit] =
    Sync[F].delay(span.log(event)).void

  override def log(fields: (String, TraceValue)*): F[Unit] = {
    val map = fields.map { case (k, v) => k -> v.value }.toMap.asJava
    Sync[F].delay(span.log(map)).void
  }

  override def makeSpan(name: String, options: Span.Options): Resource[F, Span[F]] = {
    val p = options.parentKernel.map(k =>
      tracer.extract(Format.Builtin.HTTP_HEADERS, new TextMapAdapter(k.toHeaders.asJava))
    )
    Span.putErrorFields(
      Resource
        .make(Sync[F].delay(tracer.buildSpan(name).asChildOf(p.orNull).asChildOf(span).start()))(
          s => Sync[F].delay(s.finish())
        )
        .map(LightstepSpan(tracer, _, options.spanCreationPolicy))
    )
  }

  override def spanId: F[Option[String]] =
    Sync[F].pure {
      val rawId = span.context.toSpanId
      if (rawId.nonEmpty) rawId.some else none
    }

  override def traceId: F[Option[String]] =
    Sync[F].pure {
      val rawId = span.context.toTraceId
      if (rawId.nonEmpty) rawId.some else none
    }

  // TODO
  def traceUri: F[Option[URI]] = none.pure[F]
}
