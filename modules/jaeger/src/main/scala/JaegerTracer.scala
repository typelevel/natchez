// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package jaeger

import cats.effect._
import cats.implicits._
import io.jaegertracing.Configuration
import io.jaegertracing.Configuration._
import io.jaegertracing.internal.{ JaegerTracer => NativeJaegerTracer }

object JaegerTracer {

  /** Resource that yields `Tracer[F]`s backed by `JaegerTracer`s produced by the provided program. */
  def apply[F[_]: Sync](alloc: F[NativeJaegerTracer]): Resource[F, Tracer[F]] =
    Resource.make(alloc)(t => Sync[F].delay(t.close))
      .map(Tracer.fromOpenTracing(_))

  /**
   * Resource that yields `Tracer[F]`s backed by `JaegerTracer`s produced by the provided
   * configuration program.
   */
  def initial[F[_]: Sync](system: String)(
    configure: Configuration => F[NativeJaegerTracer]
  ): Resource[F, Tracer[F]] =
    apply(Sync[F].delay(new Configuration(system)).flatMap(configure))

  /**
   * Resource that yields `Tracer[F]`s backed by `JaegerTracer`s configured from
   * environment variables. This seems to be the normal case but to my knowledge the only place
   * it's documented is in the source code for `io.jaegertracing.Configuration`.
   */
  def fromEnv[F[_]: Sync](system: String): Resource[F, Tracer[F]] =
    initial(system) { c =>
      Sync[F].delay {
        c.withSampler(SamplerConfiguration.fromEnv)
         .withReporter(ReporterConfiguration.fromEnv)
         .getTracer
      }
    }

}
