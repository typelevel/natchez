// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package lightstep

import cats.effect.{Resource, Sync}
import cats.syntax.all._
import com.lightstep.tracer.shared.Options.OptionsBuilder
import io.opentracing.Tracer
import io.opentracing.propagation.{Format, TextMapAdapter}
import natchez.Span.SpanKind
import natchez.opentracing.GlobalTracer

object Lightstep {
  def entryPoint[F[_]: Sync](configure: OptionsBuilder => F[Tracer]): Resource[F, EntryPoint[F]] = {
    val createAndRegister = configure(new OptionsBuilder).flatTap(GlobalTracer.registerTracer[F])

    Resource
      .make(createAndRegister)(t => Sync[F].delay(t.close()))
      .map(new LightstepEntryPoint[F](_))
  }

  def globalTracerEntryPoint[F[_]: Sync]: F[Option[EntryPoint[F]]] =
    GlobalTracer.fetch.map(_.map(new LightstepEntryPoint[F](_)))

  /** see https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/compatibility.md#opentracing
    */
  private[lightstep] def addLink[F[_]: Sync](
      tracer: Tracer
  )(builder: Tracer.SpanBuilder, linkKernel: Kernel): F[Tracer.SpanBuilder] =
    Sync[F].delay {
      Option(tracer.extract(Format.Builtin.HTTP_HEADERS, new TextMapAdapter(linkKernel.toJava)))
        .fold(builder)(builder.addReference("follows_from", _))
    }

  /** see https://github.com/opentracing/specification/blob/master/semantic_conventions.md#span-tags-table
    */
  private[lightstep] def addSpanKind[F[_]: Sync](
      builder: Tracer.SpanBuilder,
      spanKind: Span.SpanKind
  ): F[Unit] =
    Option(spanKind)
      .collect {
        case SpanKind.Client   => "client"
        case SpanKind.Server   => "server"
        case SpanKind.Producer => "producer"
        case SpanKind.Consumer => "consumer"
      }
      .fold(().pure[F]) { kind =>
        Sync[F].delay(builder.withTag("span.kind", kind)).void
      }
}
