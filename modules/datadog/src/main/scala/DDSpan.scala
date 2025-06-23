// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package datadog

import io.{opentracing => ot}
import cats.data.Nested
import cats.effect.{Resource, Sync}
import cats.effect.Resource.ExitCase
import cats.syntax.all._
import io.opentracing.log.Fields
import io.opentracing.propagation.{Format, TextMapAdapter}
import io.opentracing.tag.Tags
import natchez.TraceValue.{BooleanValue, NumberValue, StringValue}
import _root_.datadog.trace.api.DDTags
import natchez.Span.Options
import natchez.datadog.DDTracer.{addLink, addSpanKind}

import scala.jdk.CollectionConverters._
import java.net.URI

final case class DDSpan[F[_]: Sync](
    tracer: ot.Tracer,
    span: ot.Span,
    uriPrefix: Option[URI],
    options: Span.Options
) extends Span.Default[F] {
  override protected val spanCreationPolicyOverride: Options.SpanCreationPolicy =
    options.spanCreationPolicy

  def kernel: F[Kernel] =
    Sync[F].delay {
      val m = new java.util.HashMap[String, String]
      tracer.inject(
        span.context,
        Format.Builtin.HTTP_HEADERS,
        new TextMapAdapter(m)
      )
      Kernel.fromJava(m)
    }

  def put(fields: (String, TraceValue)*): F[Unit] =
    fields.toList.traverse_ {
      case (str, StringValue(value))  => Sync[F].delay(span.setTag(str, value))
      case (str, NumberValue(value))  => Sync[F].delay(span.setTag(str, value))
      case (str, BooleanValue(value)) => Sync[F].delay(span.setTag(str, value))
    }

  override def log(fields: (String, TraceValue)*): F[Unit] = {
    val map = fields.map { case (k, v) => k -> v.value }.toMap.asJava
    Sync[F].delay(span.log(map)).void
  }

  override def log(event: String): F[Unit] =
    Sync[F].delay(span.log(event)).void

  override def makeSpan(name: String, options: Span.Options): Resource[F, Span[F]] =
    Span.putErrorFields(
      Resource
        .makeCase(
          Sync[F]
            .delay {
              val parent = options.parentKernel.map(k =>
                tracer.extract(Format.Builtin.HTTP_HEADERS, new TextMapAdapter(k.toJava))
              )
              tracer.buildSpan(name).asChildOf(parent.orNull).asChildOf(span)
            }
            .flatTap(addSpanKind(_, options.spanKind))
            .flatMap(options.links.foldM(_)(addLink[F](tracer)))
            .flatMap(builder => Sync[F].delay(builder.start()))
        ) {
          case (span, ExitCase.Errored(e)) => Sync[F].delay(span.log(e.toString).finish())
          case (span, _)                   => Sync[F].delay(span.finish())
        }
        .flatTap(span =>
          Resource
            .make(Sync[F].delay(tracer.activateSpan(span)))(s => Sync[F].delay(s.close()))
            .whenA(options.activateSpan)
        )
        .map(DDSpan(tracer, _, uriPrefix, options))
    )

  def traceId: F[Option[String]] =
    Sync[F].pure {
      val rawId = span.context.toTraceId
      if (rawId.nonEmpty) rawId.some else none
    }

  def spanId: F[Option[String]] =
    Sync[F].pure {
      val rawId = span.context.toSpanId
      if (rawId.nonEmpty) rawId.some else none
    }

  def traceUri: F[Option[URI]] =
    (Nested(uriPrefix.pure[F]), Nested(traceId), Nested(spanId)).mapN { (uri, traceId, spanId) =>
      uri.resolve(s"/apm/trace/$traceId?spanID=$spanId")
    }.value

  override def attachError(err: Throwable, fields: (String, TraceValue)*): F[Unit] =
    put(
      Tags.ERROR.getKey -> true,
      DDTags.ERROR_MSG -> err.getMessage,
      DDTags.ERROR_TYPE -> err.getClass.getSimpleName,
      DDTags.ERROR_STACK -> err.getStackTrace.mkString
    ) >>
      Sync[F].delay {
        span.log(
          (Map(
            Fields.EVENT -> "error",
            Fields.ERROR_OBJECT -> err,
            Fields.ERROR_KIND -> err.getClass.getSimpleName,
            Fields.MESSAGE -> err.getMessage,
            Fields.STACK -> err.getStackTrace.mkString
          ) ++ fields.toList.nested.map(_.value).value.toMap).asJava
        )
      }.void
}
