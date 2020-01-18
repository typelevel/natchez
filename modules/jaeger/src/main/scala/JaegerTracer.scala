// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package jaeger

import cats.effect.{Resource, Sync}
import cats.syntax.functor._
import io.jaegertracing.Configuration
import io.jaegertracing.internal.{JaegerTracer => NativeJaegerTracer}
import natchez.opentracing.OTTracer

object Jaeger {

  def entryPoint[F[_]: Sync](system: String)(
    configure: Configuration => F[NativeJaegerTracer]
  ): Resource[F, EntryPoint[F]] =
      OTTracer.entryPoint(configure(new Configuration(system)).widen)

}
