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
import org.typelevel.ci._

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
    options: natchez.Span.Options
) extends natchez.Span.Default[F] {
  override protected val spanCreationPolicyOverride: natchez.Span.Options.SpanCreationPolicy =
    options.spanCreationPolicy

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

  override def attachError(err: Throwable, fields: (String, TraceValue)*): F[Unit] =
    put(
      ("error.message" -> TraceValue.StringValue(err.getMessage)) ::
        ("error.class" -> TraceValue.StringValue(err.getClass.getSimpleName)) ::
        fields.toList: _*
    )

  override def log(fields: (String, TraceValue)*): F[Unit] = Sync[F].unit

  override def log(event: String): F[Unit] = Sync[F].unit

  override def makeSpan(name: String, options: natchez.Span.Options): Resource[F, natchez.Span[F]] =
    Resource
      .make(NewrelicSpan.child(name, this, options))(NewrelicSpan.finish[F])
      .widen

  override def spanId: F[Option[String]] = id.some.pure[F]

  override def traceId: F[Option[String]] = traceIdS.some.pure[F]

  override def traceUri: F[Option[URI]] = none[URI].pure[F]

  /** New Relic doesn't seem to have the concept of linked spans in their data dictionary,
    * so we just attach them as a string attribute in case they end up being useful.
    */
  private def links: Option[String] =
    Option {
      options.links
        .mapFilter(_.toHeaders.get(Headers.SpanId))
        .mkString_(",")
    }.filter(_.nonEmpty)
}

object NewrelicSpan {
  object Headers {
    val TraceId = ci"X-Natchez-Trace-Id"
    val SpanId = ci"X-Natchez-Parent-Span-Id"
  }

  def fromKernel[F[_]: Sync](
      service: String,
      name: String,
      kernel: Kernel,
      options: natchez.Span.Options
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
      options = options
    )

  def root[F[_]: Sync](
      service: String,
      name: String,
      sender: SpanBatchSender,
      options: natchez.Span.Options
  ): F[NewrelicSpan[F]] =
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
      options
    )

  def child[F[_]: Sync](
      name: String,
      parent: NewrelicSpan[F],
      options: natchez.Span.Options
  ): F[NewrelicSpan[F]] =
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
      options
    )

  def finish[F[_]: Sync](nrs: NewrelicSpan[F]): F[Unit] =
    for {
      attributes <- nrs.attributes.get.map {
        nrs.links
          .foldl(_)(_.put("span.links", _))
          /*
           * see https://docs.newrelic.com/attribute-dictionary/?event=Span&attribute=span.kind
           * It's possible that only `"client"` is supported by New Relic, since it's the only value mentioned
           */
          .put("span.kind", nrs.options.spanKind.toString.toLowerCase)
      }
      finish <- Sync[F].delay(System.currentTimeMillis())
      curChildren <- nrs.children.get
      curSpan = nrs.parent
        .map(_.fold(identity, _.id))
        .foldl {
          Span
            .builder(nrs.id)
            .traceId(nrs.traceIdS)
            .name(nrs.name)
            .serviceName(nrs.service)
            .attributes(attributes)
            .durationMs((finish - nrs.startTime).toDouble)
        }(_.parentId(_))
        .build()
      _ <- nrs.parent match {
        case Some(Left(_))  => Sync[F].unit
        case Some(Right(p)) => p.children.update(curSpan :: curChildren ::: _)
        case None           =>
          val batch = new SpanBatch((curSpan :: curChildren).asJava, new Attributes(), nrs.traceIdS)
          Sync[F].delay(nrs.sender.sendBatch(batch)).void
      }
    } yield ()

}
