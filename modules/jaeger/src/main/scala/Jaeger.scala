// Copyright (c) 2019-2021 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package jaeger

import cats.effect._
import cats.syntax.all._
import io.jaegertracing.Configuration
import io.jaegertracing.internal.exceptions.UnsupportedFormatException
import io.jaegertracing.internal.{ JaegerTracer => NativeJaegerTracer }
import io.opentracing.propagation.Format
import io.opentracing.propagation.TextMapAdapter

import scala.jdk.CollectionConverters._
import java.net.URI

object Jaeger {

  def entryPoint[F[_]: Sync](
    system: String,
    uriPrefix: Option[URI] = None,
  )(
    configure: Configuration => F[NativeJaegerTracer]
  ): Resource[F, EntryPoint[F]] =
    Resource.make(
      Sync[F].delay(
        new Configuration(system)).flatMap(configure))(
        c => Sync[F].delay(c.close())
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
            )(s => Sync[F].delay(s.finish)).map(JaegerSpan(t, _, uriPrefix))

          def root(name: String): Resource[F,Span[F]] =
            Resource.make(
              Sync[F].delay(t.buildSpan(name).start()))(
              s => Sync[F].delay(s.finish)
            ).map(JaegerSpan(t, _, uriPrefix))

          def continueOrElseRoot(name: String, kernel: Kernel): Resource[F,Span[F]] =
            continue(name, kernel) flatMap {
              case null => root(name) // hurr, means headers are incomplete or invalid
              case a    => Resource.pure[F, Span[F]](a)
            } recoverWith {
              case _: UnsupportedFormatException => root(name)
            }

        }
      }

}
