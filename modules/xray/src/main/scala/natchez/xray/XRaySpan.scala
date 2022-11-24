// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez.xray

import cats._
import cats.data._
import cats.effect._
import cats.syntax.all._
import cats.effect.Resource.ExitCase
import natchez._
import natchez.TraceValue._
import cats.effect.Resource

import java.net.URI
import io.circe.JsonObject
import cats.effect.std.Random
import io.circe._
import io.circe.syntax._
import cats.effect.kernel.Resource.ExitCase.Canceled
import cats.effect.kernel.Resource.ExitCase.Errored
import cats.effect.kernel.Resource.ExitCase.Succeeded

import scala.concurrent.duration._
import scala.util.matching.Regex

private[xray] final case class XRaySpan[F[_]: Concurrent: Clock: Random](
    entry: XRayEntryPoint[F],
    name: String,
    segmentId: String,
    xrayTraceId: String,
    parent: Option[Either[String, XRaySpan[F]]],
    startTime: FiniteDuration,
    fields: Ref[F, Map[String, Json]],
    children: Ref[F, List[JsonObject]],
    sampled: Boolean,
    spanCreationPolicy: Span.Options.SpanCreationPolicy
) extends Span.Default[F] {
  import XRaySpan._

  def put(fields: (String, TraceValue)*): F[Unit] = {
    val fieldsToAdd = fields.map { case (k, v) => k -> v.asJson }
    this.fields.update(_ ++ fieldsToAdd.toMap)
  }

  def kernel: F[Kernel] =
    Kernel(Map(XRaySpan.Header -> header)).pure[F]

  def attachError(err: Throwable): F[Unit] =
    put("error.message" -> err.getMessage, "error.class" -> err.getClass.getSimpleName)

  def log(event: String): F[Unit] = Applicative[F].unit

  def log(fields: (String, TraceValue)*): F[Unit] = Applicative[F].unit

  override def makeSpan(name: String, options: Span.Options): Resource[F, Span[F]] =
    Resource.makeCase(XRaySpan.child(this, name, options.spanCreationPolicy))(XRaySpan.finish[F](_, entry, _))


  def traceId: F[Option[String]] = xrayTraceId.some.pure[F]

  def spanId: F[Option[String]] = segmentId.some.pure[F]

  def traceUri: F[Option[URI]] = none[URI].pure[F]

  /* The X-Ray documentation says to use microsecond resolution when available:
   * https://docs.aws.amazon.com/xray/latest/devguide/xray-api-segmentdocuments.html#api-segmentdocuments-fields
   */
  private def toEpochSeconds(t: FiniteDuration): Double =
    t.toMicros.toDouble / 1000000

  implicit val exceptionEncoder: Encoder.AsObject[XRayException] =
    Encoder.AsObject.instance { xex =>
      val ex = xex.ex
      JsonObject(
        "fault" -> true.asJson,
        "cause" -> Json.obj(
          "exceptions" -> Json.arr(
            Json.obj(
              "id" -> xex.id.asJson,
              "message" -> ex.getMessage.asJson,
              "type" -> ex.getClass.getName.asJson,
              "stack" -> ex.getStackTrace
                .map(x =>
                  Json.obj(
                    "line" -> x.getLineNumber.asJson,
                    "path" -> x.getFileName.asJson,
                    "label" -> x.getMethodName.asJson
                  )
                )
                .asJson
            )
          )
        )
      )
    }

  def serialize(end: FiniteDuration, exitCase: ExitCase): F[JsonObject] =
    (fields.get, children.get, XRaySpan.segmentId[F]).mapN { (fs, cs, id) =>
      val (badKeys: Map[String, Json], goodKeys: Map[String, Json]) =
        fs.partition { case (k, _) =>
          keyRegex.findFirstMatchIn(k).isDefined
        }

      val fixedAnnotations = badKeys.map { case (k, v) =>
        keyRegex.replaceAllIn(k, "_") -> v
      }
      val allAnnotations: Map[String, Json] =
        (goodKeys + ("malformed_keys" -> badKeys.keys
          .mkString(",")
          .asJson)) ++ fixedAnnotations

      JsonObject(
        "name" -> name.asJson,
        "id" -> segmentId.asJson,
        "start_time" -> toEpochSeconds(startTime).asJson,
        "end_time" -> toEpochSeconds(end).asJson,
        "trace_id" -> xrayTraceId.asJson,
        "subsegments" -> cs.reverse.map(Json.fromJsonObject).asJson,
        "annotations" -> allAnnotations.asJson
      ).deepMerge(exitCase match {
        case Canceled   => JsonObject.singleton("fault", true.asJson)
        case Errored(e) => XRayException(id, e).asJsonObject
        case Succeeded  => JsonObject.empty
      })
    }

  private def header: String =
    encodeHeader(xrayTraceId, Some(segmentId), sampled)
}

private[xray] object XRaySpan {

  private[XRaySpan] val keyRegex: Regex = """[^A-Za-z0-9_]""".r

  final case class XRayException(id: String, ex: Throwable)

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

  private[xray] def encodeHeader(
      rootId: String,
      parentId: Option[String],
      sampled: Boolean
  ): String = {
    val parent = parentId.map(p => s"Parent=$p;").getOrElse("")
    s"Root=$rootId;${parent}Sampled=${if (sampled) "1" else "0"}"
  }

  final case class XRayHeader(
      traceId: String,
      parentId: Option[String],
      sampled: Boolean
  ) {
    def toKernel: Kernel =
      Kernel(Map(Header -> encodeHeader(traceId, parentId, sampled)))
  }

  private[xray] def parseHeader(header: String): Option[XRayHeader] = {
    val foo = header
      .split(';')
      .toList
      .flatMap(_.split('=') match {
        case Array(k, v) => List((k, v))
        case _           => List.empty
      })
      .toMap

    foo
      .get("Root")
      .map(traceId => XRayHeader(traceId, foo.get("Parent"), foo.get("Sampled").contains("1")))
  }

  private def randomHexString[F[_]: Functor: Random](bytes: Int): F[String] =
    Random[F]
      .nextBytes(bytes)
      .map(x => BigInt(1, x).toString(16).reverse.padTo(bytes * 2, '0').reverse)

  private def segmentId[F[_]: Functor: Random]: F[String] =
    randomHexString[F](8)

  private def traceId[F[_]: Applicative: Clock: Random]: F[String] =
    (Clock[F].realTime, randomHexString[F](12)).mapN { (t, r) =>
      s"1-${t.toSeconds.toHexString}-$r"
    }

  def fromHeader[F[_]: Concurrent: Clock: Random](
      name: String,
      header: XRayHeader,
      entry: XRayEntryPoint[F]
  ): F[XRaySpan[F]] =
    (
      segmentId[F],
      Clock[F].realTime,
      Ref[F].of(Map.empty[String, Json]),
      Ref[F].of(List.empty[JsonObject])
    )
      .mapN { (sId, t, fields, children) =>
        XRaySpan(
          entry = entry,
          name = name,
          segmentId = sId,
          xrayTraceId = header.traceId,
          startTime = t,
          fields = fields,
          children = children,
          parent = header.parentId.map(_.asLeft),
          sampled = header.sampled,
          spanCreationPolicy = Span.Options.SpanCreationPolicy.Default
        )
      }

  def fromKernel[F[_]: Concurrent: Clock: Random: XRayEnvironment](
      name: String,
      kernel: Kernel,
      entry: XRayEntryPoint[F],
      useEnvironmentFallback: Boolean
  ): F[Option[XRaySpan[F]]] =
    OptionT
      .fromOption[F](kernel.toHeaders.get(Header))
      .subflatMap(parseHeader)
      .semiflatMap(fromHeader(name, _, entry))
      .orElse {
        OptionT
          .whenF(useEnvironmentFallback) {
            XRayEnvironment[F].kernelFromEnvironment
              .flatMap(XRaySpan.fromKernel(name, _, entry, useEnvironmentFallback = false))
          }
          .flattenOption
      }
      .value

  def fromKernelOrElseRoot[F[_]: Concurrent: Clock: Random: XRayEnvironment](
      name: String,
      kernel: Kernel,
      entry: XRayEntryPoint[F],
      useEnvironmentFallback: Boolean
  ): F[XRaySpan[F]] =
    OptionT(fromKernel(name, kernel, entry, useEnvironmentFallback))
      .getOrElseF(root(name, entry))

  def root[F[_]: Concurrent: Clock: Random](
      name: String,
      entry: XRayEntryPoint[F]
  ): F[XRaySpan[F]] =
    (
      segmentId[F],
      traceId[F],
      Clock[F].realTime,
      Ref[F].of(Map.empty[String, Json]),
      Ref[F].of(List.empty[JsonObject])
    )
      .mapN { (sId, tId, t, fields, children) =>
        XRaySpan(
          entry = entry,
          name = name,
          segmentId = sId,
          xrayTraceId = tId,
          startTime = t,
          fields = fields,
          children = children,
          parent = None,
          sampled = true,
          spanCreationPolicy = Span.Options.SpanCreationPolicy.Default
        )
      }

  def child[F[_]: Concurrent: Clock: Random](
      parent: XRaySpan[F],
      name: String,
      spanCreationPolicy: Span.Options.SpanCreationPolicy
  ): F[XRaySpan[F]] =
    (
      segmentId[F],
      Clock[F].realTime,
      Ref[F].of(Map.empty[String, Json]),
      Ref[F].of(List.empty[JsonObject])
    ).mapN { (sId, t, fields, children) =>
      XRaySpan(
        entry = parent.entry,
        name = name,
        segmentId = sId,
        xrayTraceId = parent.xrayTraceId,
        startTime = t,
        fields = fields,
        children = children,
        parent = Some(Right(parent)),
        sampled = parent.sampled,
        spanCreationPolicy = spanCreationPolicy
      )
    }

  def finish[F[_]: Clock: Monad](
      span: XRaySpan[F],
      entryPoint: XRayEntryPoint[F],
      exitCase: ExitCase
  ): F[Unit] = for {
    t <- Clock[F].realTime
    j <- span.serialize(t, exitCase)
    _ <- span.parent match {
      case None | Some(Left(_)) =>
        entryPoint.sendSegment(j) // Only send the parent segment
      case Some(Right(s)) =>
        s.children.update(j :: _) // All childrens update their parents
    }
  } yield ()

}
