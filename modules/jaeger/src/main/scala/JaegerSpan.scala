// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package jaeger

import io.{opentracing => ot}
import cats.data.Nested
import cats.effect.Sync
import cats.effect.Resource
import cats.effect.Resource.ExitCase
import cats.syntax.all._
import io.opentracing.log.Fields
import io.opentracing.propagation.Format
import io.opentracing.propagation.TextMapAdapter
import io.opentracing.tag.Tags

import scala.jdk.CollectionConverters._
import java.net.URI

private[jaeger] final case class JaegerSpan[F[_]: Sync](
    tracer: ot.Tracer,
    span: ot.Span,
    prefix: Option[URI],
    spanCreationPolicy: Span.Options.SpanCreationPolicy
) extends Span.Default[F] {
  import TraceValue._

  override def kernel: F[Kernel] =
    Sync[F].delay {
      val m = new java.util.HashMap[String, String]
      tracer.inject(
        span.context,
        Format.Builtin.HTTP_HEADERS,
        new TextMapAdapter(m)
      )
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

  override def log(fields: (String, TraceValue)*): F[Unit] = {
    val map = fields.map { case (k, v) => k -> v.value }.toMap.asJava
    Sync[F].delay(span.log(map)).void
  }

  override def log(event: String): F[Unit] =
    Sync[F].delay(span.log(event)).void

  override def makeSpan(name: String, options: Span.Options): Resource[F, Span[F]] =
    Span.putErrorFields {
      Resource.makeCase {
        val p = options.parentKernel.map(k => tracer.extract(
          Format.Builtin.HTTP_HEADERS,
          new TextMapAdapter(k.toHeaders.asJava)
        ))
        Sync[F]
          .delay(tracer.buildSpan(name).asChildOf(p.orNull).asChildOf(span).start)
          .map(JaegerSpan(tracer, _, prefix, options.spanCreationPolicy))
      }(JaegerSpan.finish)
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

  override def traceUri: F[Option[URI]] =
    (Nested(prefix.pure[F]), Nested(traceId)).mapN { (uri, id) =>
      uri.resolve(s"/trace/$id")
    }.value
}

private[jaeger] object JaegerSpan {

  def finish[F[_]: Sync]: (JaegerSpan[F], ExitCase) => F[Unit] = (outer, exitCase) => {
    val handleExit = exitCase match {
      case ExitCase.Errored(ex) =>
        Sync[F].delay {
          outer.span
            .setTag(ot.tag.Tags.ERROR.getKey, true)
            .log(
              Map(
                ot.log.Fields.EVENT -> ot.tag.Tags.ERROR.getKey,
                ot.log.Fields.ERROR_OBJECT -> ex,
                ot.log.Fields.MESSAGE -> ex.getMessage,
                ot.log.Fields.STACK -> ex.getStackTrace.mkString("\n")
              ).asJava
            )
        }.void
      case _ => Sync[F].unit
    }
    handleExit >> Sync[F].delay(outer.span.finish())
  }
}
