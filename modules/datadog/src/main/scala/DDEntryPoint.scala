// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package datadog

import cats.effect._
import cats.syntax.all._
import io.opentracing.propagation.{Format, TextMapAdapter}
import io.{opentracing => ot}
import natchez.datadog.DDTracer._

import java.net.URI

final class DDEntryPoint[F[_]: Sync](tracer: ot.Tracer, uriPrefix: Option[URI])
    extends EntryPoint[F] {
  override def root(name: String, options: Span.Options): Resource[F, Span[F]] =
    Resource
      .make {
        Sync[F]
          .delay(tracer.buildSpan(name))
          .flatTap(addSpanKind(_, options.spanKind))
          .flatMap(options.links.foldM(_)(addLink[F](tracer)))
          .flatMap(builder => Sync[F].delay(builder.start()))
      }(s => Sync[F].delay(s.finish()))
      .map(DDSpan(tracer, _, uriPrefix, options))

  override def continue(name: String, kernel: Kernel, options: Span.Options): Resource[F, Span[F]] =
    Resource
      .make {
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
      }(s => Sync[F].delay(s.finish()))
      .map(DDSpan(tracer, _, uriPrefix, options))

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
