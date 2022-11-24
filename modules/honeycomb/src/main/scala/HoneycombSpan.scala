// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez.honeycomb

import cats.effect._
import cats.effect.Resource.ExitCase
import cats.effect.Resource.ExitCase._

import cats.syntax.all._
import io.honeycomb.libhoney.HoneyClient
import java.time.Instant
import java.util.UUID
import natchez._
import java.net.URI

private[honeycomb] final case class HoneycombSpan[F[_]: Sync](
    client: HoneyClient,
    name: String,
    spanUUID: UUID,
    parentId: Option[UUID],
    traceUUID: UUID,
    timestamp: Instant,
    fields: Ref[F, Map[String, TraceValue]],
    spanCreationPolicy: Span.Options.SpanCreationPolicy
) extends Span.Default[F] {
  import HoneycombSpan._

  def get(key: String): F[Option[TraceValue]] =
    fields.get.map(_.get(key))

  def kernel: F[Kernel] =
    Kernel(
      Map(
        Headers.TraceId -> traceUUID.toString,
        Headers.SpanId -> spanUUID.toString
      )
    ).pure[F]

  def put(fields: (String, TraceValue)*): F[Unit] =
    this.fields.update(_ ++ fields.toMap)

  override def log(fields: (String, TraceValue)*): F[Unit] =
    this.fields.update(_ ++ fields.toMap)

  override def log(event: String): F[Unit] =
    log("event" -> TraceValue.StringValue(event))

  override def makeSpan(name: String, options: Span.Options): Resource[F, Span[F]] =
    Span.putErrorFields(
      Resource
        .makeCase(HoneycombSpan.child(this, name, options.spanCreationPolicy))(HoneycombSpan.finish[F])
        .widen
    )

  def traceId: F[Option[String]] =
    traceUUID.toString.some.pure[F]

  def spanId: F[Option[String]] =
    spanUUID.toString.some.pure[F]

  def traceUri: F[Option[URI]] =
    none.pure[F] // TODO

  override def attachError(err: Throwable): F[Unit] =
    put(
      "exit.case" -> "error",
      "exit.error.class" -> err.getClass.getName,
      "exit.error.message" -> err.getMessage
    )

}

private[honeycomb] object HoneycombSpan {

  object Headers {
    val TraceId = "X-Natchez-Trace-Id"
    val SpanId = "X-Natchez-Parent-Span-Id"
  }

  private def uuid[F[_]: Sync]: F[UUID] =
    Sync[F].delay(UUID.randomUUID)

  private def now[F[_]: Sync]: F[Instant] =
    Sync[F].delay(Instant.now)

  def finish[F[_]: Sync]: (HoneycombSpan[F], ExitCase) => F[Unit] = { (span, exitCase) =>
    for {
      n <- now
      fs <- span.fields.get
      e <- Sync[F].delay {
        val e = span.client.createEvent()
        e.setTimestamp(span.timestamp.toEpochMilli) // timestamp
        fs.foreach { case (k, v) => e.addField(k, v.value) } // user fields
        span.parentId.foreach(e.addField("trace.parent_id", _)) // parent trace
        e.addField("name", span.name) // and other trace fields
        e.addField("trace.span_id", span.spanUUID)
        e.addField("trace.trace_id", span.traceUUID)
        e.addField("duration_ms", n.toEpochMilli - span.timestamp.toEpochMilli)
        exitCase match {
          case Succeeded   => e.addField("exit.case", "completed")
          case Canceled    => e.addField("exit.case", "canceled")
          case Errored(ex) => span.attachError(ex)
        }
        e
      }
      _ <- Sync[F].delay(e.send())
    } yield ()
  }

  def child[F[_]: Sync](
      parent: HoneycombSpan[F],
      name: String,
      spanCreationPolicy: Span.Options.SpanCreationPolicy
  ): F[HoneycombSpan[F]] =
    for {
      spanUUID <- uuid[F]
      timestamp <- now[F]
      fields <- Ref[F].of(Map.empty[String, TraceValue])
    } yield HoneycombSpan(
      client = parent.client,
      name = name,
      spanUUID = spanUUID,
      parentId = Some(parent.spanUUID),
      traceUUID = parent.traceUUID,
      timestamp = timestamp,
      fields = fields,
      spanCreationPolicy = spanCreationPolicy
    )

  def root[F[_]: Sync](
      client: HoneyClient,
      name: String
  ): F[HoneycombSpan[F]] =
    for {
      spanUUID <- uuid[F]
      traceUUID <- uuid[F]
      timestamp <- now[F]
      fields <- Ref[F].of(Map.empty[String, TraceValue])
    } yield HoneycombSpan(
      client = client,
      name = name,
      spanUUID = spanUUID,
      parentId = None,
      traceUUID = traceUUID,
      timestamp = timestamp,
      fields = fields,
      spanCreationPolicy = Span.Options.SpanCreationPolicy.Default
    )

  def fromKernel[F[_]](
      client: HoneyClient,
      name: String,
      kernel: Kernel
  )(implicit ev: Sync[F]): F[HoneycombSpan[F]] =
    for {
      traceUUID <- ev.catchNonFatal(UUID.fromString(kernel.toHeaders(Headers.TraceId)))
      parentId <- ev.catchNonFatal(UUID.fromString(kernel.toHeaders(Headers.SpanId)))
      spanUUID <- uuid[F]
      timestamp <- now[F]
      fields <- Ref[F].of(Map.empty[String, TraceValue])
    } yield HoneycombSpan(
      client = client,
      name = name,
      spanUUID = spanUUID,
      parentId = Some(parentId),
      traceUUID = traceUUID,
      timestamp = timestamp,
      fields = fields,
      spanCreationPolicy = Span.Options.SpanCreationPolicy.Default
    )

  def fromKernelOrElseRoot[F[_]](
      client: HoneyClient,
      name: String,
      kernel: Kernel
  )(implicit ev: Sync[F]): F[HoneycombSpan[F]] =
    fromKernel(client, name, kernel).recoverWith { case _: NoSuchElementException =>
      root(client, name)
    }
}
