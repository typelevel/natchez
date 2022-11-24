// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez.newrelic

import java.net.URI
import java.util.UUID

import cats.effect.Ref
import cats.effect.{Resource, Sync}
import cats.syntax.all._
import com.newrelic.telemetry.Attributes
import com.newrelic.telemetry.spans.{Span, SpanBatch, SpanBatchSender}
import natchez.TraceValue.{BooleanValue, NumberValue, StringValue}
import natchez.newrelic.NewrelicSpan.Headers
import natchez.{Kernel, TraceValue}

import scala.jdk.CollectionConverters._

private[newrelic] final case class NewrelicSpan[F[_]: Sync](
    id: String,
    traceIdS: String,
    service: String,
    name: String,
    startTime: Long,
    attributes: Ref[F, Attributes],
    children: Ref[F, List[Span]],
    parent: Option[Either[String, NewrelicSpan[F]]],
    sender: SpanBatchSender,
    spanCreationPolicy: natchez.Span.Options.SpanCreationPolicy
) extends natchez.Span.Default[F] {

  override def kernel: F[Kernel] =
    Sync[F].delay {
      Kernel(
        Map(
          Headers.TraceId -> traceIdS,
          Headers.SpanId -> id
        )
      )

    }

  override def put(fields: (String, TraceValue)*): F[Unit] =
    fields.toList.traverse_ {
      case (k, StringValue(v))  => attributes.update(att => att.put(k, v))
      case (k, NumberValue(v))  => attributes.update(att => att.put(k, v))
      case (k, BooleanValue(v)) => attributes.update(att => att.put(k, v))
    }

  override def attachError(err: Throwable): F[Unit] =
    put("error.message" -> err.getMessage, "error.class" -> err.getClass.getSimpleName)

  override def log(fields: (String, TraceValue)*): F[Unit] = Sync[F].unit

  override def log(event: String): F[Unit] = Sync[F].unit

  override def makeSpan(name: String, options: natchez.Span.Options): Resource[F, natchez.Span[F]] =
    Resource.make(NewrelicSpan.child(name, this, options.spanCreationPolicy))(NewrelicSpan.finish[F]).widen

  override def spanId: F[Option[String]] = id.some.pure[F]

  override def traceId: F[Option[String]] = traceIdS.some.pure[F]

  override def traceUri: F[Option[URI]] = none[URI].pure[F]
}

object NewrelicSpan {
  object Headers {
    val TraceId = "X-Natchez-Trace-Id"
    val SpanId = "X-Natchez-Parent-Span-Id"
  }

  def fromKernel[F[_]: Sync](
      service: String,
      name: String,
      kernel: Kernel
  )(sender: SpanBatchSender): F[NewrelicSpan[F]] =
    for {
      traceId <- Sync[F].catchNonFatal(kernel.toHeaders(Headers.TraceId))
      parentId <- Sync[F].catchNonFatal(kernel.toHeaders(Headers.SpanId))
      spanId <- Sync[F].delay(UUID.randomUUID.toString)
      timestamp <- Sync[F].delay(System.currentTimeMillis())
      attributes <- Ref[F].of(new Attributes())
      children <- Ref[F].of(List.empty[Span])
    } yield NewrelicSpan(
      service = service,
      name = name,
      id = spanId,
      parent = Some(parentId.asLeft[NewrelicSpan[F]]),
      traceIdS = traceId,
      startTime = timestamp,
      attributes = attributes,
      children = children,
      sender = sender,
      spanCreationPolicy = natchez.Span.Options.SpanCreationPolicy.Default
    )

  def root[F[_]: Sync](service: String, name: String, sender: SpanBatchSender): F[NewrelicSpan[F]] =
    for {
      spanId <- Sync[F].delay(UUID.randomUUID().toString)
      traceId <- Sync[F].delay(UUID.randomUUID().toString)
      startTime <- Sync[F].delay(System.currentTimeMillis())
      children <- Ref[F].of(List.empty[Span])
      attributes <- Ref[F].of(new Attributes())
    } yield NewrelicSpan[F](
      spanId,
      traceId,
      service,
      name,
      startTime,
      attributes,
      children,
      None,
      sender,
      spanCreationPolicy = natchez.Span.Options.SpanCreationPolicy.Default
    )

  def child[F[_]: Sync](name: String, parent: NewrelicSpan[F], spanCreationPolicy: natchez.Span.Options.SpanCreationPolicy): F[NewrelicSpan[F]] =
    for {
      spanId <- Sync[F].delay(UUID.randomUUID().toString)
      startTime <- Sync[F].delay(System.currentTimeMillis())
      children <- Ref[F].of(List.empty[Span])
      attributes <- Ref[F].of(new Attributes())
    } yield NewrelicSpan[F](
      spanId,
      parent.traceIdS,
      parent.service,
      name,
      startTime,
      attributes,
      children,
      Some(Right(parent)),
      parent.sender,
      spanCreationPolicy = spanCreationPolicy
    )

  def finish[F[_]: Sync](nrs: NewrelicSpan[F]): F[Unit] =
    nrs.parent match {
      case Some(parent) =>
        for {
          attributes <- nrs.attributes.get
          finish <- Sync[F].delay(System.currentTimeMillis())
          curChildren <- nrs.children.get
          curSpan = Span
            .builder(nrs.id)
            .traceId(nrs.traceIdS)
            .name(nrs.name)
            .parentId(parent.fold(identity, _.id))
            .serviceName(nrs.service)
            .attributes(attributes)
            .durationMs((finish - nrs.startTime).toDouble)
            .build()
          _ <- parent match {
            case Left(_)  => Sync[F].unit
            case Right(p) => p.children.update(curSpan :: curChildren ::: _)
          }
        } yield ()
      case None =>
        for {
          attributes <- nrs.attributes.get
          finish <- Sync[F].delay(System.currentTimeMillis())
          curChildren <- nrs.children.get
          curSpan = Span
            .builder(nrs.id)
            .traceId(nrs.traceIdS)
            .name(nrs.name)
            .attributes(attributes)
            .durationMs((finish - nrs.startTime).toDouble)
            .serviceName(nrs.service)
            .build()
          batch = new SpanBatch((curSpan :: curChildren).asJava, new Attributes(), nrs.traceIdS)
          _ <- Sync[F].delay(nrs.sender.sendBatch(batch))
        } yield ()

    }
}
