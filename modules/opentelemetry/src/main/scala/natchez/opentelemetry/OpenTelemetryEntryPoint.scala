// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package opentelemetry

import cats.effect.{Resource, Sync}
import cats.syntax.all._
import io.opentelemetry.api.{OpenTelemetry => OTel}
import io.opentelemetry.api.trace.Tracer

import java.net.URI

final case class OpenTelemetryEntryPoint[F[_]: Sync](sdk: OTel, tracer: Tracer, prefix: Option[URI])
    extends EntryPoint[F] {
  override def continue(name: String, kernel: Kernel): Resource[F, Span[F]] =
    Resource
      .makeCase(OpenTelemetrySpan.fromKernel(sdk, tracer, prefix, name, kernel))(
        OpenTelemetrySpan.finish
      )
      .widen

  override def root(name: String): Resource[F, Span[F]] =
    Resource
      .makeCase(OpenTelemetrySpan.root(sdk, tracer, prefix, name))(OpenTelemetrySpan.finish)
      .widen

  override def continueOrElseRoot(name: String, kernel: Kernel): Resource[F, Span[F]] =
    Resource
      .makeCase(OpenTelemetrySpan.fromKernelOrElseRoot(sdk, tracer, prefix, name, kernel))(
        OpenTelemetrySpan.finish
      )
      .widen
}
