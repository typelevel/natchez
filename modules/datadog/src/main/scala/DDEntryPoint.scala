// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package datadog

import cats.effect.*
import cats.syntax.all.*
import io.opentracing as ot
import io.opentracing.propagation.{Format, TextMapAdapter}
import natchez.datadog.DDTracer.*

import java.net.URI

final class DDEntryPoint[F[_]: Sync](tracer: ot.Tracer, uriPrefix: Option[URI])
    extends EntryPoint[F] {
  override def root(name: String, options: Span.Options): Resource[F, Span[F]] = {
    def initSpan(): F[ot.Span] =
      Sync[F]
        .delay(tracer.buildSpan(name))
        .flatTap(addSpanKind(_, options.spanKind))
        .flatMap(options.links.foldM(_)(addLink[F](tracer)))
        .flatMap(builder => Sync[F].delay(builder.start()))

    Resource
      .make(initSpan())(s => Sync[F].delay(s.finish()))
      .flatTap(span =>
        Resource
          .make(Sync[F].delay(tracer.activateSpan(span)))(s => Sync[F].delay(s.close()))
          .whenA(options.shouldActivateSpan)
      )
      .map(DDSpan(tracer, _, uriPrefix, options))
  }

  override def continue(
      name: String,
      kernel: Kernel,
      options: Span.Options
  ): Resource[F, Span[F]] = {
    def initSpan(): F[ot.Span] =
      Sync[F]
        .delay {
          val spanContext = tracer.extract(
            Format.Builtin.HTTP_HEADERS,
            new TextMapAdapter(kernel.toJava)
          )
          tracer.buildSpan(name).asChildOf(spanContext)
        }
        .flatTap(addSpanKind(_, options.spanKind))
        .flatMap(options.links.foldM(_)(addLink[F](tracer)))
        .flatMap(builder => Sync[F].delay(builder.start()))

    Resource
      .make(initSpan())(s => Sync[F].delay(s.finish()))
      .flatTap(span =>
        Resource
          .make(Sync[F].delay(tracer.activateSpan(span)))(s => Sync[F].delay(s.close()))
          .whenA(options.shouldActivateSpan)
      )
      .map(DDSpan(tracer, _, uriPrefix, options))
  }

  override def continueOrElseRoot(
      name: String,
      kernel: Kernel,
      options: Span.Options
  ): Resource[F, Span[F]] =
    continue(name, kernel, options).flatMap {
      case null => root(name, options) // hurr, means headers are incomplete or invalid
      case span => span.pure[Resource[F, *]]
    }
}
