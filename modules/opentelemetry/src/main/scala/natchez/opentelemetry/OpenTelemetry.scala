// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package opentelemetry

import cats.syntax.all._
import cats.effect.{Async, Resource, Sync}
import io.opentelemetry.api.{GlobalOpenTelemetry, OpenTelemetry => OTel}
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.{OpenTelemetrySdk, OpenTelemetrySdkBuilder}

import java.net.URI

object OpenTelemetry {
  private final val instrumentationName = "natchez.opentelemetry"

  def entryPointFor[F[_] : Sync](otel: OTel): F[OpenTelemetryEntryPoint[F]] =
    Sync[F].delay(otel.getTracer("natchez")).map(t => 
      OpenTelemetryEntryPoint(otel, t, None)
    )

  def entryPointFor[F[_] : Sync](otel: OTel, tracer: Tracer, prefix: Option[URI]): OpenTelemetryEntryPoint[F] =
    OpenTelemetryEntryPoint(otel, tracer, prefix)

  def entryPoint[F[_] : Sync](uriPrefix: Option[URI] = None, globallyRegister: Boolean = false)(
    configure: OpenTelemetrySdkBuilder => Resource[F, OpenTelemetrySdkBuilder]
  ): Resource[F, EntryPoint[F]] = {
    val register: OpenTelemetrySdkBuilder => Resource[F, (OpenTelemetrySdk, Tracer)] = { b =>
      Resource.make(
        Sync[F].delay {
          val sdk = if (globallyRegister)
            b.buildAndRegisterGlobal()
          else
            b.build()
          val tracer = sdk.getTracer(instrumentationName)
          (sdk, tracer)
        }
      ) { case (_, _) =>
        Sync[F].delay {
          if (globallyRegister)
            GlobalOpenTelemetry.resetForTest() // this seems to be the only way to deregister it
        }
      }
    }
    Resource.eval(Sync[F].delay { OpenTelemetrySdk.builder() })
      .flatMap(configure)
      .flatMap(register)
      .map { case (sdk, tracer) =>
        OpenTelemetryEntryPoint(sdk, tracer, uriPrefix)
      }
  }

  def globalEntryPoint[F[_]: Sync](uriPrefix: Option[URI] = None): F[OpenTelemetryEntryPoint[F]] =
    Sync[F].delay {
      val ot = GlobalOpenTelemetry.get()
      OpenTelemetryEntryPoint(ot, ot.getTracer(instrumentationName), uriPrefix)
    }

  // Helper methods to help you construct Otel resources that clean themselves up
  // We need a name so the failure error can contain something useful
  def lift[F[_]: Async, T: Shutdownable](name: String, create: F[T]): Resource[F, T] =
    Resource.make(create) { t =>
      Sync[F].delay { Shutdownable[T].shutdown(t) }
        .flatMap(Utils.asyncFromCompletableResultCode(s"$name cleanup", _))
    }
}
