// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package datadog

import cats.effect._
import cats.implicits._
import _root_.datadog.opentracing.{DDTracer => NativeDDTracer}
import _root_.datadog.opentracing.DDTracer.DDTracerBuilder
import io.opentracing.propagation.{Format, TextMapAdapter}

import scala.jdk.CollectionConverters._

object DDTracer {

  def entryPoint[F[_]: Sync](
    buildFunc: DDTracerBuilder => F[NativeDDTracer]
  ): Resource[F, EntryPoint[F]] = {
    Resource.make(
      Sync[F].delay(NativeDDTracer.builder()).flatMap(buildFunc))(
      s => Sync[F].delay(s.close()).void)
      .map { tracer =>
        new EntryPoint[F] {
          override def root(name: String): Resource[F, Span[F]] =
            Resource.make(
              Sync[F].delay(tracer.buildSpan(name).start()))(
              s => Sync[F].delay(s.finish()))
            .map(DDSpan(tracer, _))

          override def continue(name: String, kernel: Kernel): Resource[F, Span[F]] =
            Resource.make(
              Sync[F].delay {
                val spanContext = tracer.extract(
                  Format.Builtin.HTTP_HEADERS,
                  new TextMapAdapter(kernel.toHeaders.asJava)
                )
                tracer.buildSpan(name).asChildOf(spanContext).start()
              }
            )(s => Sync[F].delay(s.finish())).map(DDSpan(tracer, _))

          override def continueOrElseRoot(name: String, kernel: Kernel): Resource[F, Span[F]] =
            continue(name, kernel) flatMap {
              case null => root(name) // hurr, means headers are incomplete or invalid
              case span => span.pure[Resource[F, *]]
            }
        }
      }
  }

}
