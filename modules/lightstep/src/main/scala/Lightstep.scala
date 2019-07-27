// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package lightstep

import cats.effect.{ Resource, Sync }
import cats.syntax.applicative._
import com.lightstep.tracer.jre.JRETracer
import com.lightstep.tracer.shared.Options
import io.opentracing.Tracer
import io.opentracing.propagation.{ Format, TextMapAdapter }

import scala.collection.JavaConverters._

object Lightstep {
  final case class Configuration(
    accessToken: String,
    componentName: String,
    collectorHost: String,
    collectorProtocol: String,
    collectorPort: Int
  )

  def entryPoint[F[_]: Sync](config: Configuration): Resource[F, EntryPoint[F]] =
    Resource.make(setupTracer(config))(t => Sync[F].delay(t.close())).map { t =>
      new EntryPoint[F] {
        override def root(name: String): Resource[F, Span[F]] =
          Resource
            .make(Sync[F].delay(t.buildSpan(name).start()))(s => Sync[F].delay(s.finish()))
            .map(LightstepSpan(t, _))

        override def continue(name: String, kernel: Kernel): Resource[F, Span[F]] =
          Resource.make(
            Sync[F].delay {
              val p = t.extract(Format.Builtin.HTTP_HEADERS, new TextMapAdapter(kernel.toHeaders.asJava))
              t.buildSpan(name).asChildOf(p).start()
            }
          )(s => Sync[F].delay(s.finish())).map(LightstepSpan(t, _))

        override def continueOrElseRoot(name: String, kernel: Kernel): Resource[F, Span[F]] =
          continue(name, kernel).flatMap {
            case null => root(name)
            case a    => a.pure[Resource[F, ?]]
          }
      }
    }

  private def setupTracer[F[_]: Sync](config: Configuration): F[Tracer] =
    Sync[F].delay {
      val options = new Options.OptionsBuilder()
        .withAccessToken(config.accessToken)
        .withComponentName(config.componentName)
        .withCollectorHost(config.collectorHost)
        .withCollectorProtocol(config.collectorProtocol)
        .withCollectorPort(config.collectorPort)
        .build()

      new JRETracer(options)
    }
}
