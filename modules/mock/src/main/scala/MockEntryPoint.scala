// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package mock

import cats.effect.{Resource, Sync}
import cats.syntax.all._
import io.{opentracing => ot}
import io.opentracing.mock.MockTracer
import io.opentracing.propagation.{Format, TextMapAdapter}
import natchez.Span.SpanKind

/** Implementation is inspired from other entrypoints such as
  * - JaegerEntryPoint
  * - LightstepEntryPoint
  */
final case class MockEntrypoint[F[_]: Sync]() extends EntryPoint[F] {

  val mockTracer = new MockTracer()

  override def root(name: String, options: Span.Options): Resource[F, Span[F]] =
    Resource
      .make(
        Sync[F]
          .delay(mockTracer.buildSpan(name))
          .flatMap(setOptionsAndStart(options))
      )(span => Sync[F].delay(span.finish()))
      .map(MockSpan(mockTracer, _))

  override def continue(
      name: String,
      kernel: Kernel,
      options: Span.Options
  ): Resource[F, Span[F]] =
    Resource
      .make(
        Sync[F]
          .delay {
            val spanCtxt = mockTracer.extract(
              Format.Builtin.HTTP_HEADERS,
              new TextMapAdapter(kernel.toJava)
            )
            mockTracer.buildSpan(name).asChildOf(spanCtxt)
          }
          .flatMap(setOptionsAndStart(options))
      )(span => Sync[F].delay(span.finish()))
      .map(MockSpan(mockTracer, _))

  override def continueOrElseRoot(
      name: String,
      kernel: Kernel,
      options: Span.Options
  ): Resource[F, Span[F]] =
    continue(name, kernel, options).flatMap {
      case null =>
        root(name)
      case span => Resource.pure[F, Span[F]](span)
    }

  private def spanContextFromKernel(kernel: Kernel): F[ot.SpanContext] =
    Sync[F].delay {
      mockTracer.extract(
        Format.Builtin.HTTP_HEADERS,
        new TextMapAdapter(kernel.toJava)
      )
    }

  private def setOptionsAndStart(
      options: Span.Options
  )(spanBuilder: MockTracer#SpanBuilder): F[ot.mock.MockSpan] =
    options.links
      .foldM(spanBuilder)(addLink(_)(_))
      .flatMap(setSpanKind(options.spanKind))
      .flatMap(sb => Sync[F].delay(sb.start()))

  // https://opentelemetry.io/docs/reference/specification/compatibility/opentracing/#tracer-shim
  private def addLink(
      spanBuilder: MockTracer#SpanBuilder
  )(kernel: Kernel): F[MockTracer#SpanBuilder] =
    spanContextFromKernel(kernel).flatMap { spanContext =>
      Sync[F].delay {
        spanBuilder.addReference("follows_from", spanContext)
      }
    }

  private def setSpanKind(
      spanKind: SpanKind
  )(spanBuilder: MockTracer#SpanBuilder): F[MockTracer#SpanBuilder] =
    spanKindTag
      .lift(spanKind)
      .foldM(spanBuilder)((sb, k) => Sync[F].delay(sb.withTag("span.kind", k)))

  // https://opentelemetry.io/docs/reference/specification/trace/api/#spankind
  private val spanKindTag: PartialFunction[SpanKind, String] = {
    case SpanKind.Client   => "client"
    case SpanKind.Server   => "server"
    case SpanKind.Consumer => "consumer"
    case SpanKind.Producer => "producer"
  }
}
