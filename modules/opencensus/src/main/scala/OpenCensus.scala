// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package opencensus

import cats.effect.{Resource, Sync}
import cats.syntax.functor._
import io.opencensus.exporter.trace.ocagent.{OcAgentTraceExporter, OcAgentTraceExporterConfiguration}
import io.opencensus.trace.{Sampler, Tracing}

object OpenCensus {

  def ocAgentEntryPoint[F[_]: Sync](system: String)(
      configure: OcAgentTraceExporterConfiguration.Builder => OcAgentTraceExporterConfiguration.Builder,
      sampler: Sampler): Resource[F, EntryPoint[F]] =
    Resource
      .make(
        Sync[F].delay(
          OcAgentTraceExporter.createAndRegister(configure(
            OcAgentTraceExporterConfiguration.builder().setServiceName(system))
            .build())))(_ =>
        Sync[F].delay(
          OcAgentTraceExporter.unregister()
      ))
      .flatMap(_ => Resource.liftF(entryPoint[F](sampler)))

  def entryPoint[F[_]: Sync](sampler: Sampler): F[EntryPoint[F]] =
    Sync[F]
      .delay(Tracing.getTracer)
      .map { t =>
        new EntryPoint[F] {
          def continue(name: String, kernel: Kernel): Resource[F, Span[F]] =
            Resource.makeCase(OpenCensusSpan.fromKernel(t, name, kernel))(OpenCensusSpan.finish).widen
  
          def root(name: String): Resource[F, Span[F]] =
            Resource.makeCase(OpenCensusSpan.root(t, name, sampler))(OpenCensusSpan.finish).widen
  
          def continueOrElseRoot(name: String, kernel: Kernel): Resource[F,Span[F]] =
            Resource.makeCase(OpenCensusSpan.fromKernelOrElseRoot(t, name, kernel, sampler))(OpenCensusSpan.finish).widen
        }
      }
}
