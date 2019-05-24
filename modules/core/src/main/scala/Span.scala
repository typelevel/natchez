// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.effect._
import cats.implicits._
import scala.collection.JavaConverters._
import io.opentracing.propagation.Format
import io.opentracing.propagation.TextMapAdapter

trait Span[F[_]] {
  def setTag(key: String, value: TraceValue): F[Unit]
  def getBaggageItem(key: String): F[Option[String]]
  def setBaggageItem(key: String, value: String): F[Unit]
  def span(label: String): Resource[F, Span[F]]
  def log(fields: Map[String, TraceValue]): F[Unit]
  def toHttpHeaders: F[Map[String, String]]
}

object Span {

  def fromOpenTracing[F[_]: Sync](
    otTracer: io.opentracing.Tracer,
    otSpan:   io.opentracing.Span
  ): Span[F] =
    new Span[F] {

      def log(fields: Map[String,TraceValue]): F[Unit] =
        Sync[F].delay(otSpan.log(fields.mapValues(_.value).asJava)).void

      def span(label: String): Resource[F, Span[F]] =
        Resource.make(
          Sync[F].delay(otTracer.buildSpan(label).asChildOf(otSpan).start()))(
          s => Sync[F].delay(s.finish())
        ).map(fromOpenTracing(otTracer, _))

      def setTag(key: String, value: TraceValue): F[Unit] =
        Sync[F].delay {
          value match {
            case StringValue(s)  => otSpan.setTag(key, s)
            case BooleanValue(b) => otSpan.setTag(key, b)
            case NumberValue(n)  => otSpan.setTag(key, n)
          }
        } .void

      def getBaggageItem(key: String): F[Option[String]]   =
        Sync[F].delay(Option(otSpan.getBaggageItem(key)))

      def setBaggageItem(key: String, value: String): F[Unit] =
        Sync[F].delay(otSpan.setBaggageItem(key, value)).void

      def toHttpHeaders: F[Map[String, String]] =
        Sync[F].delay {
          val m = new java.util.HashMap[String, String]
          otTracer.inject(
            otSpan.context,
            Format.Builtin.HTTP_HEADERS,
            new TextMapAdapter(m)
          )
          m.asScala.toMap
        }

    }

}
