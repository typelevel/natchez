// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package datadog

import io.{opentracing => ot}
import cats.effect.{Resource, Sync}
import cats.effect.Resource.ExitCase
import cats.syntax.all._
import io.opentracing.propagation.{Format, TextMapAdapter}
import natchez.TraceValue.{BooleanValue, NumberValue, StringValue}
import scala.jdk.CollectionConverters._
import java.net.URI

private[datadog] final case class DDSpan[F[_]: Sync](
  tracer: ot.Tracer,
  span:   ot.Span
) extends Span[F] {
  def kernel: F[Kernel] =
  Sync[F].delay {
    val m = new java.util.HashMap[String, String]
    tracer.inject(
      span.context,
      Format.Builtin.HTTP_HEADERS,
      new TextMapAdapter(m)
    )
    Kernel(m.asScala.toMap)
  }

  def put(fields: (String, TraceValue)*): F[Unit] =
    fields.toList.traverse_ {
      case (str, StringValue(value)) => Sync[F].delay(span.setTag(str, value))
      case (str, NumberValue(value)) => Sync[F].delay(span.setTag(str, value))
      case (str, BooleanValue(value)) => Sync[F].delay(span.setTag(str, value))
    }

  def span(name: String): Resource[F,Span[F]] =
    Resource.makeCase(
      Sync[F].delay(tracer.buildSpan(name).asChildOf(span).start)) {
      case (span, ExitCase.Errored(e)) => Sync[F].delay(span.log(e.toString).finish())
      case (span, _) => Sync[F].delay(span.finish())
    }.map(DDSpan(tracer, _))

  // TODO
  def traceId: F[Option[String]] = none.pure[F]
  def traceUri: F[Option[URI]] = none.pure[F]

}
