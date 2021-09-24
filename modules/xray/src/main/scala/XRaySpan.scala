// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez.xray

import cats.effect._
import cats.syntax.all._
import cats.effect.Resource.ExitCase

import natchez._
import natchez.TraceValue._
import cats.effect.Resource
import java.net.URI
import java.time.Instant
import io.circe.JsonObject
import cats.effect.std.Random
import io.circe._
import io.circe.syntax._
import cats.effect.kernel.Resource.ExitCase.Canceled
import cats.effect.kernel.Resource.ExitCase.Errored
import cats.effect.kernel.Resource.ExitCase.Succeeded

private[xray] final case class XRaySpan[F[_]: Sync](
    entry: XRayEntryPoint[F],
    name: String,
    segmentId: String,
    xrayTraceId: String,
    parent: Option[Either[String, XRaySpan[F]]],
    startTime: Instant,
    fields: Ref[F, Map[String, Json]],
    children: Ref[F, List[JsonObject]],
    sampled: Boolean
) extends Span[F] {
  import XRaySpan._

  def put(fields: (String, TraceValue)*): F[Unit] = {
    val fieldsToAdd = fields.map { case (k, v) => (k -> v.asJson) }
    this.fields.update(_ ++ fieldsToAdd.toMap)
  }

  def kernel: F[Kernel] =
    Kernel(Map(XRaySpan.Header -> header)).pure[F]

  def span(name: String): Resource[F, Span[F]] =
    Resource.makeCase(XRaySpan.child(this, name))(
      XRaySpan.finish[F](_, entry, _)
    )

  def traceId: F[Option[String]] = xrayTraceId.some.pure[F]

  def spanId: F[Option[String]] = segmentId.some.pure[F]

  def traceUri: F[Option[URI]] = Option.empty.pure[F]

  private def toEpoch(t: Instant): Double =
    t.getEpochSecond().toDouble + t.getNano().toDouble / 1000000000

  def serialize(end: Instant, exitCase: ExitCase): F[JsonObject] =
    (fields.get, children.get, XRaySpan.segmentId).mapN { (fs, cs, id) =>
      def exitFields(ex: Throwable): List[(String, Json)] = List(
        "fault" -> true.asJson,
        "cause" -> Json.obj(
          "exceptions" -> Json.arr(
            Json.obj(
              "id" -> id.asJson,
              "message" -> ex.getMessage().asJson,
              "type" -> ex.getClass().getName().asJson,
              "stack" -> ex
                .getStackTrace()
                .map(x =>
                  Json.obj(
                    "line" -> x.getLineNumber().asJson,
                    "path" -> x.getFileName().asJson,
                    "label" -> x.getMethodName().asJson
                  )
                )
                .asJson
            )
          )
        )
      )

      val fields: List[(String, Json)] =
        List(
          "name" -> name.asJson,
          "id" -> segmentId.asJson,
          "start_time" -> toEpoch(startTime).asJson,
          "end_time" -> toEpoch(end).asJson,
          "trace_id" -> xrayTraceId.asJson,
          "subsegments" -> cs.reverse.map(Json.fromJsonObject).asJson,
          "annotations" -> fs.asJson
        ) ++ {
          exitCase match {
            case Canceled   => List("fault" -> true.asJson)
            case Errored(e) => exitFields(e)
            case Succeeded  => List()
          }
        }

      JsonObject.fromIterable(fields)
    }

  private def header: String =
    s"Root=$xrayTraceId;Parent=$segmentId;Sampled=${if (sampled) "1" else "0"}"

}

private[xray] object XRaySpan {

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

  val Header = "X-Amzn-Trace-Id"

  final case class XRayHeader(
      traceId: String,
      parentId: Option[String],
      sampled: Boolean
  )

  private def parseHeader(header: String): Option[XRayHeader] = {
    val foo = header
      .split(';')
      .toList
      .flatMap(_.split('=') match {
        case Array(k, v) => List((k, v))
        case _           => List.empty
      })
      .toList
      .toMap

    foo
      .get("Root")
      .map(traceId =>
        XRayHeader(traceId, foo.get("Parent"), foo.get("Sampled").contains("1"))
      )
  }

  private def now[F[_]: Sync]: F[Instant] =
    Sync[F].delay(Instant.now)

  private def randomHexString[F[_]: Sync](bytes: Int): F[String] =
    Random.scalaUtilRandom
      .flatMap(_.nextBytes(bytes))
      .map(x => BigInt(1, x).toString(16).reverse.padTo(bytes * 2, '0').reverse)

  private def segmentId[F[_]: Sync]: F[String] =
    randomHexString(8)

  private def traceId[F[_]: Sync]: F[String] = for {
    t <- now
    r <- randomHexString(12)
  } yield s"1-${t.getEpochSecond.toHexString}-$r"

  def fromHeader[F[_]: Sync](
      name: String,
      header: XRayHeader,
      entry: XRayEntryPoint[F]
  ): F[XRaySpan[F]] = for {
    sId <- segmentId
    t <- now
    fields <- Ref[F].of(Map.empty[String, Json])
    children <- Ref[F].of(List.empty[JsonObject])
  } yield XRaySpan(
    entry = entry,
    name = name,
    segmentId = sId,
    xrayTraceId = header.traceId,
    startTime = t,
    fields = fields,
    children = children,
    parent = header.parentId.map(_.asLeft),
    sampled = header.sampled
  )

  def fromKernel[F[_]: Sync](
      name: String,
      kernel: Kernel,
      entry: XRayEntryPoint[F]
  ): F[XRaySpan[F]] =
    kernel.toHeaders
      .get(Header)
      .flatMap(parseHeader)
      .map(x => fromHeader(name, x, entry))
      .get

  def fromKernelOrElseRoot[F[_]: Sync](
      name: String,
      kernel: Kernel,
      entry: XRayEntryPoint[F]
  ): F[XRaySpan[F]] =
    kernel.toHeaders
      .get(Header)
      .flatMap(parseHeader)
      .map(x => fromHeader(name, x, entry))
      .getOrElse(root(name, entry))

  def root[F[_]: Sync](name: String, entry: XRayEntryPoint[F]): F[XRaySpan[F]] =
    for {
      sId <- segmentId
      tId <- traceId
      t <- now
      fields <- Ref[F].of(Map.empty[String, Json])
      children <- Ref[F].of(List.empty[JsonObject])
    } yield XRaySpan(
      entry = entry,
      name = name,
      segmentId = sId,
      xrayTraceId = tId,
      startTime = t,
      fields = fields,
      children = children,
      parent = None,
      sampled = true
    )

  def child[F[_]: Sync](parent: XRaySpan[F], name: String): F[XRaySpan[F]] =
    for {
      sId <- segmentId
      t <- now
      fields <- Ref[F].of(Map.empty[String, Json])
      children <- Ref[F].of(List.empty[JsonObject])
    } yield XRaySpan(
      entry = parent.entry,
      name = name,
      segmentId = sId,
      xrayTraceId = parent.xrayTraceId,
      startTime = t,
      fields = fields,
      children = children,
      parent = Some(Right(parent)),
      sampled = parent.sampled
    )

  def finish[F[_]: Sync](
      span: XRaySpan[F],
      entryPoint: XRayEntryPoint[F],
      exitCase: ExitCase
  ): F[Unit] = for {
    t <- now
    j <- span.serialize(t, exitCase)
    _ <- span.parent match {
      case None | Some(Left(_)) =>
        entryPoint.sendSegment(j) // Only send the parent segment
      case Some(Right(s)) =>
        s.children.update(j :: _) // All childrens update their parents
    }
  } yield ()

}
