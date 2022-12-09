// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez.log

import cats.effect.Ref
import cats.effect._
import cats.effect.std.UUIDGen
import cats.effect.Resource.ExitCase
import cats.effect.Resource.ExitCase._
import cats.syntax.all._

import java.time.Instant
import java.util.UUID
import natchez._
import natchez.TraceValue._
import io.circe.Json
import io.circe.Encoder
import io.circe.syntax._
import io.circe.JsonObject
import org.typelevel.log4cats.Logger

import java.net.URI

private[log] final case class LogSpan[F[_]: Sync: Logger](
    service: String,
    name: String,
    sid: String,
    parent: Option[Either[String, LogSpan[F]]],
    parentKernel: Option[Kernel],
    traceID: String,
    timestamp: Instant,
    fields: Ref[F, Map[String, Json]],
    children: Ref[F, List[JsonObject]],
    spanCreationPolicy: Span.Options.SpanCreationPolicy
) extends Span.Default[F] {
  import LogSpan._

  def parentId: Option[String] =
    parent.map(_.fold(identity, _.sid))

  def get(key: String): F[Option[Json]] =
    fields.get.map(_.get(key))

  def kernel: F[Kernel] =
    Kernel(
      Map(
        Headers.TraceId -> traceID,
        Headers.SpanId -> sid
      )
    ).pure[F]

  def put(fields: (String, TraceValue)*): F[Unit] =
    putAny(fields.map { case (k, v) => k -> v.asJson }: _*)

  def putAny(fields: (String, Json)*): F[Unit] =
    this.fields.update(_ ++ fields.toMap)

  override def log(fields: (String, TraceValue)*): F[Unit] =
    put(fields: _*)

  override def log(event: String): F[Unit] =
    log("event" -> TraceValue.StringValue(event))

  def makeSpan(label: String, options: Span.Options): Resource[F, Span[F]] =
    Span.putErrorFields(
      Resource.makeCase(LogSpan.child(this, label, options))(LogSpan.finishChild[F]).widen
    )

  def attachError(err: Throwable): F[Unit] =
    putAny(
      "exit.case" -> "error".asJson,
      "exit.error.class" -> err.getClass.getName.asJson,
      "exit.error.message" -> err.getMessage.asJson,
      "exit.error.stackTrace" -> err.getStackTrace.map(_.toString).asJson
    )

  def json(finish: Instant): F[JsonObject] =
    (fields.get, children.get).mapN { (fs, cs) =>
      // Assemble our JSON object such that the Natchez fields always come first, in the same
      // order, followed by error fields (if any), followed by span fields.

      val fields: List[(String, Json)] =
        List(
          "name" -> name.asJson,
          "service" -> service.asJson,
          "timestamp" -> timestamp.asJson,
          "duration_ms" -> (finish.toEpochMilli - timestamp.toEpochMilli).asJson,
          "trace.span_id" -> sid.asJson,
          "trace.parent_id" -> parentId.asJson,
          "trace.trace_id" -> traceID.asJson
        ) ++ fs ++ List("children" -> cs.reverse.map(Json.fromJsonObject).asJson)

      JsonObject.fromIterable(fields)

    }

  def traceId: F[Option[String]] =
    traceID.some.pure[F]

  def spanId: F[Option[String]] =
    sid.some.pure[F]

  def traceUri: F[Option[URI]] = none.pure[F]
}

private[log] object LogSpan {

  implicit val EncodeTraceValue: Encoder[TraceValue] =
    Encoder.instance {
      case StringValue(s)                       => s.asJson
      case BooleanValue(b)                      => b.asJson
      case NumberValue(n: java.lang.Byte)       => n.asJson
      case NumberValue(n: java.lang.Short)      => n.asJson
      case NumberValue(n: java.lang.Integer)    => n.asJson
      case NumberValue(n: java.lang.Long)       => n.asJson
      case NumberValue(n: java.lang.Float)      => n.asJson
      case NumberValue(n: java.lang.Double)     => n.asJson
      case NumberValue(n: java.math.BigDecimal) => n.asJson
      case NumberValue(n: java.math.BigInteger) => n.asJson
      case NumberValue(n: BigDecimal)           => n.asJson
      case NumberValue(n: BigInt)               => n.asJson
      case NumberValue(n)                       => n.doubleValue.asJson
    }

  object Headers {
    val TraceId = "X-Natchez-Trace-Id"
    val SpanId = "X-Natchez-Parent-Span-Id"
  }

  private def uuid[F[_]: Sync]: F[UUID] =
    UUIDGen.randomUUID

  private def now[F[_]: Sync]: F[Instant] =
    Sync[F].delay(Instant.now)

  def finish[F[_]: Sync: Logger](format: Json => String): (LogSpan[F], ExitCase) => F[Unit] = {
    (span, exitCase) =>
      for {
        n <- now
        _ <- exitCase match {
          case Succeeded => span.put("exit.case" -> "succeeded")
          case Canceled  => span.put("exit.case" -> "canceled")
          case Errored(ex: Fields) =>
            span.attachError(ex) >> span.putAny(ex.fields.toList.map { case (k, v) =>
              (k, v.asJson)
            }: _*)
          case Errored(ex) => span.attachError(ex)
        }
        j <- span.json(n)
        _ <- span.parent match {
          case None | Some(Left(_)) => Logger[F].info(format(Json.fromJsonObject(j)))
          case Some(Right(s))       => s.children.update(j :: _)
        }
      } yield ()
  }

  def finishChild[F[_]: Sync: Logger]: (LogSpan[F], ExitCase) => F[Unit] =
    finish(_ => sys.error("implementation error; child JSON should never be logged"))

  def child[F[_]: Sync: Logger](
      parent: LogSpan[F],
      name: String,
      options: Span.Options
  ): F[LogSpan[F]] =
    for {
      spanId <- uuid[F]
      timestamp <- now[F]
      fields <- Ref[F].of(Map.empty[String, Json])
      children <- Ref[F].of(List.empty[JsonObject])
    } yield LogSpan(
      service = parent.service,
      name = name,
      sid = spanId.toString,
      parent = Some(Right(parent)),
      parentKernel = options.parentKernel,
      traceID = parent.traceID,
      timestamp = timestamp,
      fields = fields,
      children = children,
      spanCreationPolicy = options.spanCreationPolicy
    )

  def root[F[_]: Sync: Logger](
      service: String,
      name: String
  ): F[LogSpan[F]] =
    for {
      spanId <- uuid[F]
      traceID <- uuid[F]
      timestamp <- now[F]
      fields <- Ref[F].of(Map.empty[String, Json])
      children <- Ref[F].of(List.empty[JsonObject])
    } yield LogSpan(
      service = service,
      name = name,
      sid = spanId.toString,
      parent = None,
      parentKernel = None,
      traceID = traceID.toString,
      timestamp = timestamp,
      fields = fields,
      children = children,
      spanCreationPolicy = Span.Options.SpanCreationPolicy.Default
    )

  def fromKernel[F[_]: Sync: Logger](
      service: String,
      name: String,
      kernel: Kernel
  ): F[LogSpan[F]] =
    for {
      traceID <- Sync[F].catchNonFatal(kernel.toHeaders(Headers.TraceId))
      parentId <- Sync[F].catchNonFatal(kernel.toHeaders(Headers.SpanId))
      spanId <- uuid[F]
      timestamp <- now[F]
      fields <- Ref[F].of(Map.empty[String, Json])
      children <- Ref[F].of(List.empty[JsonObject])
    } yield LogSpan(
      service = service,
      name = name,
      sid = spanId.toString,
      parent = Some(Left(parentId)),
      parentKernel = None,
      traceID = traceID,
      timestamp = timestamp,
      fields = fields,
      children = children,
      spanCreationPolicy = Span.Options.SpanCreationPolicy.Default
    )

  def fromKernelOrElseRoot[F[_]: Sync: Logger](
      service: String,
      name: String,
      kernel: Kernel
  ): F[LogSpan[F]] =
    fromKernel(service, name, kernel).recoverWith { case _: NoSuchElementException =>
      root(service, name)
    }
}
