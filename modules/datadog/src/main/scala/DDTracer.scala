// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package datadog

import java.net.URI

import cats.effect._
import cats.syntax.all._
import _root_.datadog.opentracing.{DDTracer => NativeDDTracer}
import _root_.datadog.opentracing.DDTracer.DDTracerBuilder
import natchez.opentracing.GlobalTracer

object DDTracer {
  def entryPoint[F[_]: Sync](
    buildFunc: DDTracerBuilder => F[NativeDDTracer],
    uriPrefix: Option[URI] = None
  ): Resource[F, EntryPoint[F]] = {
    val createAndRegister = 
      Sync[F].delay(NativeDDTracer.builder())
        .flatMap(buildFunc)
        .flatTap(GlobalTracer.registerTracer[F])

    Resource.make(createAndRegister)(t => Sync[F].delay(t.close()))
        .map(new DDEntryPoint[F](_, uriPrefix))
  }

  def globalTracerEntryPoint[F[_]: Sync](uriPrefix: Option[URI]): F[Option[EntryPoint[F]]] = 
    GlobalTracer.fetch.map(_.map(new DDEntryPoint[F](_, uriPrefix)))
}
