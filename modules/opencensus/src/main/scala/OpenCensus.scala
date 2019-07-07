// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package opencensus

import cats.effect.{Resource, Sync}
import cats.syntax.applicative._
import cats.syntax.applicativeError._
import cats.syntax.functor._
import io.opencensus.exporter.trace.ocagent.{
  OcAgentTraceExporter,
  OcAgentTraceExporterConfiguration
}
import io.opencensus.trace.Tracing
import io.opencensus.trace.propagation.SpanContextParseException
import io.opencensus.trace.propagation.TextFormat.Getter

object OpenCensus {

  def ocAgentEntryPoint[F[_]: Sync](system: String)(
      configure: OcAgentTraceExporterConfiguration.Builder => OcAgentTraceExporterConfiguration.Builder)
    : Resource[F, EntryPoint[F]] =
    Resource
      .make(
        Sync[F].delay(
          OcAgentTraceExporter.createAndRegister(configure(
            OcAgentTraceExporterConfiguration.builder().setServiceName(system))
            .build())))(_ =>
        Sync[F].delay(
          OcAgentTraceExporter.unregister()
      ))
      .flatMap(_ => Resource.liftF(entryPoint[F]))

  def entryPoint[F[_]: Sync]: F[EntryPoint[F]] =
    Sync[F]
      .delay(Tracing.getTracer)
      .map { t =>
        new EntryPoint[F] {
          override def root(name: String): Resource[F, Span[F]] =
            Resource
              .make(
                Sync[F].delay(t.spanBuilder(name).startSpan())
              )(s => Sync[F].delay(s.end()))
              .map(OpenCensusSpan(t, _))

          override def continue(name: String,
                                kernel: Kernel): Resource[F, Span[F]] =
            Resource
              .make(
                Sync[F].delay {
                  val ctx = Tracing.getPropagationComponent.getB3Format
                    .extract(kernel, spanContextGetter)
                  t.spanBuilderWithRemoteParent(name, ctx).startSpan()
                }
              )(s => Sync[F].delay(s.end()))
              .map(OpenCensusSpan(t, _))

          override def continueOrElseRoot(
              name: String,
              kernel: Kernel): Resource[F, Span[F]] =
            continue(name, kernel) flatMap (
              _.pure[Resource[F, ?]]
            ) recoverWith {
              case _: SpanContextParseException => root(name)
              case _: NullPointerException =>
                root(name) // means headers are incomplete or invalid
            }
        }
      }

  private val spanContextGetter: Getter[Kernel] = new Getter[Kernel] {
    override def get(carrier: Kernel, key: String): String =
      carrier.toHeaders(key)
  }
}
