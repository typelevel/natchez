// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package lightstep

import cats.effect.{ Resource, Sync }
import cats.implicits._
import com.lightstep.tracer.shared.Options.OptionsBuilder
import io.opentracing.Tracer
import natchez.opentracing.GlobalTracer

object Lightstep {
  def entryPoint[F[_]: Sync](configure: OptionsBuilder => F[Tracer]): Resource[F, EntryPoint[F]] = {
    val createAndRegister = configure(new OptionsBuilder).flatTap(GlobalTracer.registerTracer[F])

    Resource.fromAutoCloseable(createAndRegister)
      .map(new LightstepEntryPoint[F](_))
  }

  def globalTracerEntryPoint[F[_]: Sync]: F[Option[EntryPoint[F]]] =
    GlobalTracer.fetch.map(_.map(new LightstepEntryPoint[F](_)))
}
