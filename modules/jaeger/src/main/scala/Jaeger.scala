// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package jaeger

import cats.effect._
import cats.syntax.all._
import io.jaegertracing.Configuration
import io.jaegertracing.internal.{ JaegerTracer => NativeJaegerTracer }
import natchez.opentracing.GlobalTracer

import java.net.URI

object Jaeger {

  def entryPoint[F[_]: Sync](
    system: String,
    uriPrefix: Option[URI] = None,
  )(
    configure: Configuration => F[NativeJaegerTracer]
  ): Resource[F, EntryPoint[F]] = {
    val createAndRegister = 
      Sync[F].delay(new Configuration(system))
        .flatMap(configure)
        .flatTap(GlobalTracer.registerTracer[F])
    
    Resource.make(createAndRegister)(c => Sync[F].delay(c.close()))
        .map { new JaegerEntryPoint[F](_, uriPrefix) }
  }
  
  def globalTracerEntryPoint[F[_]: Sync](uriPrefix: Option[URI]): F[Option[EntryPoint[F]]] = 
    GlobalTracer.fetch.map(_.map(new JaegerEntryPoint[F](_, uriPrefix)))
}
