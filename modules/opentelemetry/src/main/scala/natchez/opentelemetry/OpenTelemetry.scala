// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez.opentelemetry

import cats.syntax.all._
import cats.effect.{Resource, Sync}
import io.opentelemetry.sdk.OpenTelemetrySdk
import natchez.{EntryPoint, Kernel, Span}

object OpenTelemetry {
  def entryPointForSdk[F[_] : Sync](sdk: OpenTelemetrySdk): F[EntryPoint[F]] =
    Sync[F]
      .delay(sdk.getTracer("natchez"))
      .map { t =>
        new EntryPoint[F] {
          def continue(name: String, kernel: Kernel): Resource[F, Span[F]] =
            Resource.makeCase(OpenTelemetrySpan.fromKernel(sdk, t, name, kernel))(OpenTelemetrySpan.finish).widen

          def root(name: String): Resource[F, Span[F]] =
            Resource.makeCase(OpenTelemetrySpan.root(sdk, t, name))(OpenTelemetrySpan.finish).widen

          def continueOrElseRoot(name: String, kernel: Kernel): Resource[F, Span[F]] =
            Resource
              .makeCase(OpenTelemetrySpan.fromKernelOrElseRoot(sdk, t, name, kernel))(OpenTelemetrySpan.finish)
              .widen
        }
      }
}
