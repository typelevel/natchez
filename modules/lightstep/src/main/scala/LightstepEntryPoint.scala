// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package lightstep

import cats.effect.{Resource, Sync}
import cats.syntax.all._
import io.opentracing.Tracer
import io.opentracing.propagation.{Format, TextMapAdapter}
import natchez.lightstep.Lightstep._

final class LightstepEntryPoint[F[_]: Sync](tracer: Tracer) extends EntryPoint[F] {
  override def root(name: String, options: Span.Options): Resource[F, Span[F]] =
    Resource
      .make {
        Sync[F]
          .delay(tracer.buildSpan(name))
          .flatTap(addSpanKind(_, options.spanKind))
          .flatMap(options.links.foldM(_)(addLink[F](tracer)))
          .flatMap(builder => Sync[F].delay(builder.start()))
      }(s => Sync[F].delay(s.finish()))
      .map(LightstepSpan(tracer, _, options))

  override def continue(name: String, kernel: Kernel, options: Span.Options): Resource[F, Span[F]] =
    Resource
      .make(
        Sync[F]
          .delay {
            val p =
              tracer.extract(Format.Builtin.HTTP_HEADERS, new TextMapAdapter(kernel.toJava))
            tracer.buildSpan(name).asChildOf(p)
          }
          .flatTap(addSpanKind(_, options.spanKind))
          .flatMap(options.links.foldM(_)(addLink[F](tracer)))
          .flatMap(builder => Sync[F].delay(builder.start()))
      )(s => Sync[F].delay(s.finish()))
      .map(LightstepSpan(tracer, _, options))

  override def continueOrElseRoot(
      name: String,
      kernel: Kernel,
      options: Span.Options
  ): Resource[F, Span[F]] =
    continue(name, kernel, options).flatMap {
      case null => root(name, options)
      case a    => a.pure[Resource[F, *]]
    }
}
