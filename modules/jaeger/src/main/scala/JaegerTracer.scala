// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package jaeger

import cats.effect._
import cats.implicits._
import io.jaegertracing.Configuration
import io.jaegertracing.internal.exceptions.UnsupportedFormatException
import io.jaegertracing.internal.{ JaegerTracer => NativeJaegerTracer }
import io.opentracing.propagation.Format
import io.opentracing.propagation.TextMapAdapter

import scala.jdk.CollectionConverters._

object Jaeger {

  def entryPoint[F[_]: Sync](system: String)(
    configure: Configuration => F[NativeJaegerTracer]
  ): Resource[F, EntryPoint[F]] =
    Resource.make(
      Sync[F].delay(
        new Configuration(system)).flatMap(configure))(
        c => Sync[F].delay(c.close)
      ).map { t =>
        new EntryPoint[F] {

          def continue(name: String, kernel: Kernel): Resource[F,Span[F]] =
            Resource.make(
              Sync[F].delay {
                val p = t.extract(
                  Format.Builtin.HTTP_HEADERS,
                  new TextMapAdapter(kernel.toHeaders.asJava)
                )
                t.buildSpan(name).asChildOf(p).start()
              }
            )(s => Sync[F].delay(s.finish)).map(JaegerSpan(t, _))

          def root(name: String): Resource[F,Span[F]] =
            Resource.make(
              Sync[F].delay(t.buildSpan(name).start()))(
              s => Sync[F].delay(s.finish)
            ).map(JaegerSpan(t, _))

          def continueOrElseRoot(name: String, kernel: Kernel): Resource[F,Span[F]] =
            continue(name, kernel) flatMap {
              case null => root(name) // hurr, means headers are incomplete or invalid
              case a    => a.pure[Resource[F, ?]]
            } recoverWith {
              case _: UnsupportedFormatException => root(name)
            }

        }
      }

}
