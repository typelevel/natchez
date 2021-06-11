// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package opentelemetry

import cats.effect.{Resource, Sync}
import io.opentelemetry.api.{OpenTelemetry => OtelOpenTelemetry}
import io.opentelemetry.api.trace.{Span => OtelSpan, Tracer => OtelTracer}
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter

import java.lang
import java.net.URI
import scala.jdk.CollectionConverters._

final case class OpenTelemetryEntryPoint[F[_]: Sync](sdk: OtelOpenTelemetry, tracer: OtelTracer, uriPrefix: Option[URI]) extends EntryPoint[F] {
  override def root(name: String): Resource[F, Span[F]] =
    Resource.make[F, OtelSpan] (
      Sync[F].delay { tracer.spanBuilder(name).startSpan() }
    ) { s =>
      Sync[F].delay { s.end() }
    }.map { span =>
      OpenTelemetrySpan(sdk, tracer, span, uriPrefix)
    }

  object MapGetter extends TextMapGetter[Map[String, String]] {
    override def keys(carrier: Map[String, String]): lang.Iterable[String] = carrier.keys.asJava
    override def get(carrier: Map[String, String], key: String): String = carrier.get(key).orNull
  }

  override def continue(name: String, kernel: Kernel): Resource[F, Span[F]] =
    Resource.make(
      Sync[F].delay {
        val c = Context.root()
        val p = sdk.getPropagators.getTextMapPropagator.extract(
          c,
          kernel.toHeaders,
          MapGetter
        )
        tracer.spanBuilder(name).setParent(p).startSpan()
      }
    ) { s =>
      Sync[F].delay { s.end() }
    }.map(OpenTelemetrySpan(sdk, tracer, _, uriPrefix))

  override def continueOrElseRoot(name: String, kernel: Kernel): Resource[F, Span[F]] =
    continue(name, kernel) // this is already the behaviour of `continue` since it defaults to not changing the context
}
