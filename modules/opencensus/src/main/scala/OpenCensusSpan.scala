// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package opencensus

import cats.effect._
import cats.effect.Resource.ExitCase
import cats.effect.Resource.ExitCase._
import cats.syntax.all._
import io.opencensus.trace.propagation.TextFormat.Setter
import io.opencensus.trace.{AttributeValue, Sampler, SpanBuilder, Tracer, Tracing}
import io.opencensus.trace.propagation.SpanContextParseException
import io.opencensus.trace.propagation.TextFormat.Getter
import natchez.Span.{Options, SpanKind}
import natchez.TraceValue.{BooleanValue, NumberValue, StringValue}
import org.typelevel.ci._

import scala.collection.mutable
import java.net.URI
import scala.jdk.CollectionConverters._

private[opencensus] final case class OpenCensusSpan[F[_]: Sync](
    tracer: Tracer,
    span: io.opencensus.trace.Span,
    options: Span.Options
) extends Span.Default[F] {

  import OpenCensusSpan._

  override protected val spanCreationPolicyOverride: Options.SpanCreationPolicy =
    options.spanCreationPolicy

  private def traceToAttribute(value: TraceValue): AttributeValue = value match {
    case StringValue(v) =>
      val safeString = if (v == null) "null" else v
      AttributeValue.stringAttributeValue(safeString)
    case NumberValue(v) =>
      AttributeValue.doubleAttributeValue(v.doubleValue())
    case BooleanValue(v) =>
      AttributeValue.booleanAttributeValue(v)
  }

  override def put(fields: (String, TraceValue)*): F[Unit] =
    fields.toList.traverse_ { case (key, value) =>
      Sync[F].delay(span.putAttribute(key, traceToAttribute(value)))
    }

  override def log(fields: (String, TraceValue)*): F[Unit] = {
    val map = fields.map { case (k, v) => k -> traceToAttribute(v) }.toMap.asJava
    Sync[F].delay(span.addAnnotation("event", map)).void
  }

  override def log(event: String): F[Unit] =
    Sync[F].delay(span.addAnnotation(event)).void

  override def kernel: F[Kernel] = Sync[F].delay {
    val headers: mutable.Map[CIString, String] = mutable.Map.empty[CIString, String]
    Tracing.getPropagationComponent.getB3Format
      .inject(span.getContext, headers, spanContextSetter)
    Kernel(headers.toMap)
  }

  override def makeSpan(name: String, options: Span.Options): Resource[F, Span[F]] =
    Span.putErrorFields(
      Resource
        .makeCase(options.parentKernel match {
          case None    => OpenCensusSpan.child(this, name, options)
          case Some(k) =>
            OpenCensusSpan.fromKernelWithSpan(tracer, name, k, span, options)
        })(
          OpenCensusSpan.finish
        )
        .widen
    )

  def traceId: F[Option[String]] =
    Sync[F].pure {
      val rawId = span.getContext.getTraceId.toLowerBase16
      if (rawId.nonEmpty) rawId.some else none
    }

  def spanId: F[Option[String]] =
    Sync[F].pure {
      val rawId = span.getContext.getSpanId.toLowerBase16
      if (rawId.nonEmpty) rawId.some else none
    }

  def traceUri: F[Option[URI]] = none.pure[F]

  override def attachError(err: Throwable, fields: (String, TraceValue)*): F[Unit] =
    put(
      ("error.message" -> TraceValue.StringValue(err.getMessage)) ::
        ("error.class" -> TraceValue.StringValue(err.getClass.getSimpleName)) ::
        fields.toList: _*
    )
}

private[opencensus] object OpenCensusSpan {
  private val spanContextSetter = new Setter[mutable.Map[CIString, String]] {
    override def put(carrier: mutable.Map[CIString, String], key: String, value: String): Unit = {
      carrier.put(CIString(key), value)
      ()
    }
  }

  def finish[F[_]: Sync]: (OpenCensusSpan[F], ExitCase) => F[Unit] = { (outer, exitCase) =>
    for {
      // collect error details, if any
      _ <- exitCase.some
        .collect { case Errored(t: Fields) =>
          t.fields.toList
        }
        .traverse(outer.put)
      _ <- Sync[F].delay {
        exitCase match {
          case Succeeded   => outer.span.setStatus(io.opencensus.trace.Status.OK)
          case Canceled    => outer.span.setStatus(io.opencensus.trace.Status.CANCELLED)
          case Errored(ex) => outer.attachError(ex)
        }
      }
      _ <- outer.options.links
        .traverse { k =>
          Sync[F].delay {
            Tracing.getPropagationComponent.getB3Format
              .extract(k, spanContextGetter)
              .getSpanId
              .toLowerBase16
          }
        }
        .flatMap { linkedSpanIds =>
          // OpenCensus only has full support for parent and child links, so attach them as a field
          outer.put("span.links" -> linkedSpanIds.mkString_(","))
        }
      _ <- Sync[F].delay(outer.span.end())
    } yield ()
  }

  def child[F[_]: Sync](
      parent: OpenCensusSpan[F],
      name: String,
      options: Span.Options
  ): F[OpenCensusSpan[F]] =
    Sync[F]
      .delay(parent.tracer.spanBuilderWithExplicitParent(name, parent.span))
      .flatTap(setOptionsOnBuilder[F](options))
      .flatMap(startSpan[F])
      .map(OpenCensusSpan(parent.tracer, _, options))

  def root[F[_]: Sync](
      tracer: Tracer,
      name: String,
      sampler: Sampler,
      options: Span.Options
  ): F[OpenCensusSpan[F]] =
    Sync[F]
      .delay(tracer.spanBuilder(name).setSampler(sampler))
      .flatTap(setOptionsOnBuilder[F](options))
      .flatMap(startSpan[F])
      .map(OpenCensusSpan(tracer, _, options))

  def fromKernelWithSpan[F[_]: Sync](
      tracer: Tracer,
      name: String,
      kernel: Kernel,
      span: io.opencensus.trace.Span,
      options: Span.Options
  ): F[OpenCensusSpan[F]] =
    Sync[F]
      .delay {
        val ctx = Tracing.getPropagationComponent.getB3Format
          .extract(kernel, spanContextGetter)
        tracer
          .spanBuilderWithRemoteParent(name, ctx)
          .setParentLinks(List(span).asJava)
      }
      .flatTap(setOptionsOnBuilder[F](options))
      .flatMap(startSpan[F])
      .map(OpenCensusSpan(tracer, _, options))

  def fromKernel[F[_]: Sync](
      tracer: Tracer,
      name: String,
      kernel: Kernel,
      options: Span.Options
  ): F[OpenCensusSpan[F]] =
    Sync[F]
      .delay {
        val ctx = Tracing.getPropagationComponent.getB3Format
          .extract(kernel, spanContextGetter)
        tracer.spanBuilderWithRemoteParent(name, ctx)
      }
      .flatTap(setOptionsOnBuilder[F](options))
      .flatMap(startSpan[F])
      .map(OpenCensusSpan(tracer, _, options))

  def fromKernelOrElseRoot[F[_]](
      tracer: Tracer,
      name: String,
      kernel: Kernel,
      sampler: Sampler,
      options: Span.Options
  )(implicit ev: Sync[F]): F[OpenCensusSpan[F]] =
    fromKernel(tracer, name, kernel, options).recoverWith {
      case _: SpanContextParseException =>
        root(tracer, name, sampler, options)
      case _: NoSuchElementException =>
        root(tracer, name, sampler, options) // means headers are incomplete or invalid
      case _: NullPointerException =>
        root(tracer, name, sampler, options) // means headers are incomplete or invalid
    }

  private val spanContextGetter: Getter[Kernel] = (carrier: Kernel, key: String) =>
    carrier.toHeaders(CIString(key))

  private def setOptionsOnBuilder[F[_]: Sync](
      options: Span.Options
  )(builder: SpanBuilder): F[Unit] =
    options.spanKind match {
      case SpanKind.Server =>
        Sync[F].delay(builder.setSpanKind(io.opencensus.trace.Span.Kind.SERVER)).void
      case SpanKind.Client =>
        Sync[F].delay(builder.setSpanKind(io.opencensus.trace.Span.Kind.CLIENT)).void
      case _ =>
        // OpenCensus only supports Server and Client span types, so ignore other types
        ().pure[F]
    }

  private def startSpan[F[_]: Sync](builder: SpanBuilder): F[io.opencensus.trace.Span] =
    Sync[F].delay(builder.startSpan())
}
