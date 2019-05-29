// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez.honeycomb

import cats.effect.Sync
import cats.implicits._
import java.util.UUID
import natchez.Span
import cats.effect.Resource
import io.honeycomb.libhoney.HoneyClient
import natchez.TraceValue
import cats.effect.concurrent.Ref
import java.time.Instant
import java.util.Base64
import java.nio.charset.Charset
import cats.MonadError

final case class HoneycombSpan[F[_]: Sync](
  client:    HoneyClient,
  name:      String,
  spanId:    UUID,
  parentId:  Option[UUID],
  traceId:   UUID,
  timestamp: Instant,
  fields:    Ref[F, Map[String, Any]], // per HoneyClient
  baggage:   Ref[F, Map[String, String]]
) extends Span[F] {
  import HoneycombSpan._

  def finish: F[Unit] =
    for {
      n  <- now
      fs <- fields.get
      _  <- Sync[F].delay {
              val e = client.createEvent()
              e.setTimestamp(timestamp.toEpochMilli)             // timestamp
              fs.foreach { case (k, v) => e.addField(k, v) }     // user fields
              parentId.foreach(e.addField("trace.parent_id", _)) // parent trace
              e.addField("name",           name)                 // and other trace fields
              e.addField("trace.span_id",  spanId)
              e.addField("trace.trace_id", traceId)
              e.addField("duration_ms",    (n.toEpochMilli - timestamp.toEpochMilli))
              e.send()
            }
    } yield ()

  def getBaggageItem(key: String): F[Option[String]] =
    baggage.get.map(_ get key)

  // here we just treat a log as very short span ... is this reasonable?
  def log(fields: Map[String, TraceValue]): F[Unit] =
    span("log").use(s => fields.toList.traverse_ { case (k, v) => s.setTag(k, v) })

  def setBaggageItem(key: String, value: String): F[Unit] =
    baggage.update(_ + (key -> value))

  def setTag(key: String, value: TraceValue): F[Unit] =
    fields.update(_ + (key -> value.value))

  def toHttpHeaders: F[Map[String,String]] =
    baggage.get.map { m =>
      m.toList.zipWithIndex.map { case ((k, v), n) =>
        Headers.baggage(n) -> encode(k, v)
      } .toMap ++ Map(
        Headers.TraceId    -> traceId.toString,
        Headers.SpanId     -> spanId.toString
      )
    }

  def span(name: String): Resource[F, Span[F]] =
    Resource.make(HoneycombSpan.child(this, name))(_.finish).widen[Span[F]]

}

object HoneycombSpan {

  private val charset = Charset.forName("UTF-8")
  private val encoder = Base64.getEncoder
  private val decoder = Base64.getDecoder

  object Headers {

    val BaggagePrefix = "X-Natchez-Honeycomb-Baggage"
    val TraceId       = "X-Natchez-Trace-Id"
    val SpanId        = "X-Natchez-Parent-Span-Id"

    def baggage(n: Int): String =
      new StringBuilder(BaggagePrefix).append("-").append(n).toString

  }

  private def encode(k: String, v: String): String =
    new StringBuilder()
      .append(encoder.encodeToString(k.getBytes(charset)))
      .append(",")
      .append(encoder.encodeToString(v.getBytes(charset)))
      .toString

  def decode[F[_]](kv: String)(
    implicit M: MonadError[F, Throwable]
  ): F[(String, String)] =
    kv.split(",") match {
      case Array(ek, ev) =>
        for {
          k <- M.catchNonFatal(new String(decoder.decode(ek), charset))
          v <- M.catchNonFatal(new String(decoder.decode(ev), charset))
        } yield (k, v)
      case _=>
        M.raiseError(new IllegalArgumentException(s"Cannot decode header value $kv"))
    }

  private def uuid[F[_]: Sync]: F[UUID] =
    Sync[F].delay(UUID.randomUUID)

  private def now[F[_]: Sync]: F[Instant] =
    Sync[F].delay(Instant.now)

  def child[F[_]: Sync](
    parent: HoneycombSpan[F],
    name:   String
  ): F[HoneycombSpan[F]] =
    for {
      spanId    <- uuid[F]
      timestamp <- now[F]
      fields    <- Ref[F].of(Map.empty[String, Any])
      baggage   <- parent.baggage.get.flatMap(Ref[F].of)
    } yield HoneycombSpan(
      client    = parent.client,
      name      = name,
      spanId    = spanId,
      parentId  = Some(parent.spanId),
      traceId   = parent.traceId,
      timestamp = timestamp,
      fields    = fields,
      baggage   = baggage
    )

  def root[F[_]: Sync](
    client:    HoneyClient,
    name:      String,
    baggage:   Map[String, String] = Map.empty
  ): F[HoneycombSpan[F]] =
    for {
      spanId    <- uuid[F]
      traceId   <- uuid[F]
      timestamp <- now[F]
      fields    <- Ref[F].of(Map.empty[String, Any])
      baggage   <- Ref[F].of(baggage)
    } yield HoneycombSpan(
      client    = client,
      name      = name,
      spanId    = spanId,
      parentId  = None,
      traceId   = traceId,
      timestamp = timestamp,
      fields    = fields,
      baggage   = baggage
    )

  def fromHttpHeaders[F[_]](
    client:      HoneyClient,
    name:        String,
    httpHeaders: Map[String,String]
  )(implicit ev: Sync[F]): F[HoneycombSpan[F]] =
    for {
      traceId  <- ev.catchNonFatal(UUID.fromString(httpHeaders(Headers.TraceId)))
      parentId <- ev.catchNonFatal(UUID.fromString(httpHeaders(Headers.SpanId)))
      baggage  <- httpHeaders
                    .filterKeys(_.startsWith(Headers.BaggagePrefix))
                    .values
                    .toList
                    .traverse(decode[F](_))
                    .map(_.toMap)
      spanId    <- uuid[F]
      timestamp <- now[F]
      fields    <- Ref[F].of(Map.empty[String, Any])
      baggage   <- Ref[F].of(baggage)
    } yield HoneycombSpan(
      client    = client,
      name      = name,
      spanId    = spanId,
      parentId  = Some(parentId),
      traceId   = traceId,
      timestamp = timestamp,
      fields    = fields,
      baggage   = baggage
    )

}
