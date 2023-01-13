// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package jaeger

import cats.effect._
import cats.syntax.all._
import io.jaegertracing.internal.exceptions.UnsupportedFormatException
import io.opentracing.propagation.{Format, TextMapAdapter}
import io.{opentracing => ot}
import natchez.Span.SpanKind

import java.net.URI

final class JaegerEntryPoint[F[_]: Sync](tracer: ot.Tracer, uriPrefix: Option[URI])
    extends EntryPoint[F] {
  override def continue(name: String, kernel: Kernel, options: Span.Options): Resource[F, Span[F]] =
    Resource
      .make(
        spanContextFromKernel(kernel)
          .flatMap { p =>
            Sync[F].delay {
              tracer
                .buildSpan(name)
                .asChildOf(p)
            }
          }
          .flatMap(setOptionsAndStart(options))
      )(s => Sync[F].delay(s.finish))
      .map(JaegerSpan(tracer, _, uriPrefix, options.spanCreationPolicy))

  override def root(name: String, options: Span.Options): Resource[F, Span[F]] =
    Resource
      .make(Sync[F].delay(tracer.buildSpan(name)).flatMap(setOptionsAndStart(options)))(s =>
        Sync[F].delay(s.finish)
      )
      .map(JaegerSpan(tracer, _, uriPrefix, options.spanCreationPolicy))

  override def continueOrElseRoot(
      name: String,
      kernel: Kernel,
      options: Span.Options
  ): Resource[F, Span[F]] =
    continue(name, kernel, options)
      .flatMap {
        case null => root(name, options) // hurr, means headers are incomplete or invalid
        case a    => Resource.pure[F, Span[F]](a)
      }
      .recoverWith { case _: UnsupportedFormatException =>
        root(name)
      }

  private def spanContextFromKernel(kernel: Kernel): F[ot.SpanContext] =
    Sync[F].delay {
      tracer.extract(
        Format.Builtin.HTTP_HEADERS,
        new TextMapAdapter(kernel.toJava)
      )
    }

  private def setOptionsAndStart(
      options: Span.Options
  )(spanBuilder: ot.Tracer.SpanBuilder): F[ot.Span] =
    options.links
      .foldM(spanBuilder)(addLink(_)(_))
      .flatMap(setSpanKind(options.spanKind))
      .flatMap(sb => Sync[F].delay(sb.start()))

  private def addLink(
      spanBuilder: ot.Tracer.SpanBuilder
  )(kernel: Kernel): F[ot.Tracer.SpanBuilder] =
    spanContextFromKernel(kernel).flatMap { spanContext =>
      Sync[F].delay {
        spanBuilder.addReference("FOLLOWS_FROM", spanContext)
      }
    }

  private def setSpanKind(
      spanKind: SpanKind
  )(spanBuilder: ot.Tracer.SpanBuilder): F[ot.Tracer.SpanBuilder] =
    spanKindTag
      .lift(spanKind)
      .foldM(spanBuilder)((sb, k) => Sync[F].delay(sb.withTag("span.kind", k)))

  // mapping defined at https://opentelemetry.io/docs/reference/specification/trace/sdk_exporters/jaeger/#spankind
  private val spanKindTag: PartialFunction[SpanKind, String] = {
    case SpanKind.Client   => "client"
    case SpanKind.Server   => "server"
    case SpanKind.Consumer => "consumer"
    case SpanKind.Producer => "producer"
  }
}
