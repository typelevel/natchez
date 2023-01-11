// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez.logodin

import cats.Applicative
import cats.effect._
import cats.effect.Resource.ExitCase
import cats.effect.Resource.ExitCase._
import cats.implicits._
import java.time.Instant
import java.util.UUID
import natchez._
import natchez.TraceValue._
import io.circe.Json
import io.circe.Encoder
import io.circe.syntax._
import io.circe.JsonObject
import io.odin.Logger
import java.net.URI
import org.typelevel.ci._

private[logodin] final case class LogSpan[F[_]: Sync: Logger](
    service: String,
    name: String,
    sid: UUID,
    parent: Option[Either[UUID, LogSpan[F]]],
    tid: UUID,
    timestamp: Instant,
    fields: Ref[F, Map[String, Json]],
    children: Ref[F, List[JsonObject]],
    spanCreationPolicy: Span.Options.SpanCreationPolicy
) extends Span.Default[F] {
  import LogSpan._

  def spanId: F[Option[String]] =
    sid.toString.some.pure[F]

  def traceId: F[Option[String]] =
    tid.toString.some.pure[F]

  def traceUri: F[Option[URI]] =
    none.pure[F]

  def parentId: Option[UUID] =
    parent.map(_.fold(identity, _.tid))

  def get(key: String): F[Option[Json]] =
    fields.get.map(_.get(key))

  def kernel: F[Kernel] =
    Kernel(
      Map(
        Headers.TraceId -> tid.toString,
        Headers.SpanId -> sid.toString
      )
    ).pure[F]

  def put(fields: (String, TraceValue)*): F[Unit] =
    putAny(fields.map { case (k, v) => k -> v.asJson }: _*)

  def putAny(fields: (String, Json)*): F[Unit] =
    this.fields.update(_ ++ fields.toMap)

  override def attachError(err: Throwable, fields: (String, TraceValue)*): F[Unit] =
    put(
      ("error.message" -> TraceValue.StringValue(err.getMessage)) ::
        ("error.class" -> TraceValue.StringValue(err.getClass.getSimpleName)) ::
        fields.toList: _*
    )

  def log(event: String): F[Unit] = Applicative[F].unit

  def log(fields: (String, TraceValue)*): F[Unit] = Applicative[F].unit

  def makeSpan(label: String, options: Span.Options): Resource[F, Span[F]] =
    Resource
      .makeCase(LogSpan.child(this, label, options.spanCreationPolicy))(LogSpan.finish[F])
      .widen

  def json(finish: Instant, exitCase: ExitCase): F[JsonObject] =
    (fields.get, children.get).mapN { (fs, cs) =>
      // Assemble our JSON object such that the Natchez fields always come first, in the same
      // order, followed by error fields (if any), followed by span fields.

      def exitFields(ex: Throwable): List[(String, Json)] =
        List(
          "exit.case" -> "error".asJson,
          "exit.error.class" -> ex.getClass.getName.asJson,
          "exit.error.message" -> ex.getMessage.asJson,
          "exit.error.stackTrace" -> ex.getStackTrace.map(_.toString).asJson
        )

      val fields: List[(String, Json)] =
        List(
          "name" -> name.asJson,
          "service" -> service.asJson,
          "timestamp" -> timestamp.asJson,
          "duration_ms" -> (finish.toEpochMilli - timestamp.toEpochMilli).asJson,
          "trace.span_id" -> sid.asJson,
          "trace.parent_id" -> parentId.asJson,
          "trace.trace_id" -> tid.asJson
        ) ++ {
          exitCase match {
            case Succeeded           => List("exit.case" -> "completed".asJson)
            case Canceled            => List("exit.case" -> "canceled".asJson)
            case Errored(ex: Fields) => exitFields(ex) ++ ex.fields.toList.map(_.fmap(_.asJson))
            case Errored(ex)         => exitFields(ex)
          }
        } ++ fs ++ List("children" -> cs.reverse.map(Json.fromJsonObject).asJson)

      JsonObject.fromIterable(fields)

    }
}

private[logodin] object LogSpan {

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
    val TraceId = ci"X-Natchez-Trace-Id"
    val SpanId = ci"X-Natchez-Parent-Span-Id"
  }

  private def uuid[F[_]: Sync]: F[UUID] =
    Sync[F].delay(UUID.randomUUID)

  private def now[F[_]: Sync]: F[Instant] =
    Sync[F].delay(Instant.now)

  def finish[F[_]: Sync: Logger]: (LogSpan[F], ExitCase) => F[Unit] = { (span, exitCase) =>
    for {
      n <- now
      j <- span.json(n, exitCase)
      _ <- span.parent match {
        case None | Some(Left(_)) => Logger[F].info(Json.fromJsonObject(j).spaces2)
        case Some(Right(s))       => s.children.update(j :: _)
      }
    } yield ()
  }

  def child[F[_]: Sync: Logger](
      parent: LogSpan[F],
      name: String,
      spanCreationPolicy: Span.Options.SpanCreationPolicy
  ): F[LogSpan[F]] =
    for {
      spanId <- uuid[F]
      timestamp <- now[F]
      fields <- Ref[F].of(Map.empty[String, Json])
      children <- Ref[F].of(List.empty[JsonObject])
    } yield LogSpan(
      service = parent.service,
      name = name,
      sid = spanId,
      parent = Some(Right(parent)),
      tid = parent.tid,
      timestamp = timestamp,
      fields = fields,
      children = children,
      spanCreationPolicy = spanCreationPolicy
    )

  def root[F[_]: Sync: Logger](
      service: String,
      name: String
  ): F[LogSpan[F]] =
    for {
      spanId <- uuid[F]
      traceId <- uuid[F]
      timestamp <- now[F]
      fields <- Ref[F].of(Map.empty[String, Json])
      children <- Ref[F].of(List.empty[JsonObject])
    } yield LogSpan(
      service = service,
      name = name,
      sid = spanId,
      parent = None,
      tid = traceId,
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
      traceId <- Sync[F].catchNonFatal(UUID.fromString(kernel.toHeaders(Headers.TraceId)))
      parentId <- Sync[F].catchNonFatal(UUID.fromString(kernel.toHeaders(Headers.SpanId)))
      spanId <- uuid[F]
      timestamp <- now[F]
      fields <- Ref[F].of(Map.empty[String, Json])
      children <- Ref[F].of(List.empty[JsonObject])
    } yield LogSpan(
      service = service,
      name = name,
      sid = spanId,
      parent = Some(Left(parentId)),
      tid = traceId,
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
