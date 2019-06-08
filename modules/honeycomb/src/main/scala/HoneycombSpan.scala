// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez.honeycomb

import cats.effect.concurrent.Ref
import cats.effect._
import cats.effect.ExitCase.Canceled
import cats.effect.ExitCase.Completed
import cats.implicits._
import io.honeycomb.libhoney.HoneyClient
import java.time.Instant
import java.util.UUID
import natchez._

private[honeycomb] final case class HoneycombSpan[F[_]: Sync](
  client:    HoneyClient,
  name:      String,
  spanId:    UUID,
  parentId:  Option[UUID],
  traceId:   UUID,
  timestamp: Instant,
  fields:    Ref[F, Map[String, TraceValue]]
) extends Span[F] {
  import HoneycombSpan._

  def get(key: String): F[Option[TraceValue]] =
    fields.get.map(_.get(key))

  def kernel: F[Kernel] =
    Kernel(Map(
      Headers.TraceId -> traceId.toString,
      Headers.SpanId  -> spanId.toString
    )).pure[F]

  def put(fields: (String, TraceValue)*): F[Unit] =
    this.fields.update(_ ++ fields.toMap)

  def span(label: String): Resource[F, Span[F]] =
    Resource.makeCase(HoneycombSpan.child(this, label))(HoneycombSpan.finish[F]).widen

}

private[honeycomb] object HoneycombSpan {

  object Headers {
    val TraceId       = "X-Natchez-Trace-Id"
    val SpanId        = "X-Natchez-Parent-Span-Id"
  }

  private def uuid[F[_]: Sync]: F[UUID] =
    Sync[F].delay(UUID.randomUUID)

  private def now[F[_]: Sync]: F[Instant] =
    Sync[F].delay(Instant.now)

  def finish[F[_]: Sync]: (HoneycombSpan[F], ExitCase[Throwable]) => F[Unit] = { (span, exitCase) =>
    for {
      // collect error details, if any
      _  <- exitCase.some.collect {
              case ExitCase.Error(t: Fields) => t.fields
            } .traverse(m => span.fields.update(_ ++ m))
      n  <- now
      fs <- span.fields.get
      e  <- Sync[F].delay {
              val e = span.client.createEvent()
              e.setTimestamp(span.timestamp.toEpochMilli)             // timestamp
              fs.foreach { case (k, v) => e.addField(k, v.value) }    // user fields
              span.parentId.foreach(e.addField("trace.parent_id", _)) // parent trace
              e.addField("name",           span.name)                 // and other trace fields
              e.addField("trace.span_id",  span.spanId)
              e.addField("trace.trace_id", span.traceId)
              e.addField("duration_ms",    n.toEpochMilli - span.timestamp.toEpochMilli)
              exitCase match {
                case Completed          => e.addField("exit.case", "completed")
                case Canceled           => e.addField("exit.case", "canceled")
                case ExitCase.Error(ex) =>
                  e.addField("exit.case",          "error")
                  e.addField("exit.error.class",    ex.getClass.getName)
                  e.addField("exit.error.message",  ex.getMessage)
              }
              e
            }
      _  <- Sync[F].delay(e.send())
    } yield ()
  }

  def child[F[_]: Sync](
    parent: HoneycombSpan[F],
    name:   String
  ): F[HoneycombSpan[F]] =
    for {
      spanId    <- uuid[F]
      timestamp <- now[F]
      fields    <- Ref[F].of(Map.empty[String, TraceValue])
    } yield HoneycombSpan(
      client    = parent.client,
      name      = name,
      spanId    = spanId,
      parentId  = Some(parent.spanId),
      traceId   = parent.traceId,
      timestamp = timestamp,
      fields    = fields
    )

  def root[F[_]: Sync](
    client:    HoneyClient,
    name:      String
  ): F[HoneycombSpan[F]] =
    for {
      spanId    <- uuid[F]
      traceId   <- uuid[F]
      timestamp <- now[F]
      fields    <- Ref[F].of(Map.empty[String, TraceValue])
    } yield HoneycombSpan(
      client    = client,
      name      = name,
      spanId    = spanId,
      parentId  = None,
      traceId   = traceId,
      timestamp = timestamp,
      fields    = fields
    )

  def fromKernel[F[_]](
    client: HoneyClient,
    name:   String,
    kernel: Kernel
  )(implicit ev: Sync[F]): F[HoneycombSpan[F]] =
    for {
      traceId  <- ev.catchNonFatal(UUID.fromString(kernel.toHeaders(Headers.TraceId)))
      parentId <- ev.catchNonFatal(UUID.fromString(kernel.toHeaders(Headers.SpanId)))
      spanId    <- uuid[F]
      timestamp <- now[F]
      fields    <- Ref[F].of(Map.empty[String, TraceValue])
    } yield HoneycombSpan(
      client    = client,
      name      = name,
      spanId    = spanId,
      parentId  = Some(parentId),
      traceId   = traceId,
      timestamp = timestamp,
      fields    = fields
    )

  def fromKernelOrElseRoot[F[_]](
    client: HoneyClient,
    name:   String,
    kernel: Kernel
  )(implicit ev: Sync[F]): F[HoneycombSpan[F]] =
    fromKernel(client, name, kernel).recoverWith {
      case _: NoSuchElementException => root(client, name)
    }
}
