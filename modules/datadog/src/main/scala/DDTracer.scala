// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package datadog

import java.net.URI
import cats.effect._
import cats.syntax.all._
import _root_.datadog.opentracing.{DDTracer => NativeDDTracer}
import _root_.datadog.opentracing.DDTracer.DDTracerBuilder
import io.opentracing.Tracer
import io.opentracing.propagation.{Format, TextMapAdapter}
import natchez.opentracing.GlobalTracer

object DDTracer {
  def entryPoint[F[_]: Sync](
      buildFunc: DDTracerBuilder => F[NativeDDTracer],
      uriPrefix: Option[URI] = None
  ): Resource[F, EntryPoint[F]] = {
    val createAndRegister =
      Sync[F]
        .delay(NativeDDTracer.builder())
        .flatMap(buildFunc)
        .flatTap(GlobalTracer.registerTracer[F])

    Resource
      .make(createAndRegister)(t => Sync[F].delay(t.close()))
      .map(new DDEntryPoint[F](_, uriPrefix))
  }

  def globalTracerEntryPoint[F[_]: Sync](uriPrefix: Option[URI]): F[Option[EntryPoint[F]]] =
    GlobalTracer.fetch.map(_.map(new DDEntryPoint[F](_, uriPrefix)))

  /** see https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/compatibility.md#opentracing
    */
  private[datadog] def addLink[F[_]: Sync](
      tracer: Tracer
  )(builder: Tracer.SpanBuilder, linkKernel: Kernel): F[Tracer.SpanBuilder] =
    Sync[F].delay {
      Option(tracer.extract(Format.Builtin.HTTP_HEADERS, new TextMapAdapter(linkKernel.toJava)))
        .fold(builder)(builder.addReference("follows_from", _))
    }

  /** see https://github.com/opentracing/specification/blob/master/semantic_conventions.md#span-tags-table
    */
  private[datadog] def addSpanKind[F[_]: Sync](
      builder: Tracer.SpanBuilder,
      spanKind: Span.SpanKind
  ): F[Unit] =
    Option(spanKind)
      .collect {
        case Span.SpanKind.Client   => "client"
        case Span.SpanKind.Server   => "server"
        case Span.SpanKind.Producer => "producer"
        case Span.SpanKind.Consumer => "consumer"
      }
      .fold(().pure[F]) { kind =>
        Sync[F].delay(builder.withTag("span.kind", kind)).void
      }
}
