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
import natchez.opentracing.OTEntryPoint
import natchez.opentracing.MakeSpanUri

object DDTracer {

  def entryPoint[F[_]: Sync](
    buildFunc: DDTracerBuilder => F[NativeDDTracer],
  ): Resource[F, EntryPoint[F]] = {
    val createAndRegister = Sync[F]
      .delay(NativeDDTracer.builder()).flatMap(buildFunc)
      .flatTap(GlobalTracer.registerTracer[F])

    Resource
      .fromAutoCloseable(createAndRegister)
      .map(
        new OTEntryPoint[F](
          _,
          Some(makeSpanUri),
        )
      )
  }

  def globalTracerEntryPoint[F[_]: Sync]: F[Option[EntryPoint[F]]] =
    GlobalTracer.fetch.map(_.map(new OTEntryPoint[F](_, Some(makeSpanUri))))

  private val makeSpanUri: MakeSpanUri = (trace, span) => new URI(s"https://app.datadoghq.com/apm/trace/$trace?spanId=$span")
}
