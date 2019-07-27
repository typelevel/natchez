// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package lightstep

import cats.effect.{ Sync, Resource }
import com.lightstep.tracer.jre.JRETracer
import com.lightstep.tracer.shared.Options
import io.opentracing.Tracer

object Lightstep {
  // TODO: use refined types to properly specify the config
  final case class Configuration(
    accessToken: String,
    componentName: String,
    collectorHost: String,
    collectorProtocol: String,
    collectorPort: Int
  )

  def entryPoint[F[_]: Sync](config: Configuration): Resource[F, EntryPoint[F]] =
    Resource.make(setupTracer(config))(t => Sync[F].delay(t.close())).map { _ =>
      ???
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
