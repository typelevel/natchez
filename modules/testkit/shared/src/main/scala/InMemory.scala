// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats._
import cats.data._
import cats.effect.{Trace => _, _}
import cats.syntax.all._
import natchez.Span.Options

import java.net.URI

object InMemory {

  class Span[F[_]: Applicative](
      lineage: Lineage,
      k: Kernel,
      ref: Ref[F, Chain[(Lineage, NatchezCommand)]],
      val options: Options
  ) extends natchez.Span.Default[F] {
    override protected val spanCreationPolicyOverride: Options.SpanCreationPolicy =
      options.spanCreationPolicy

    def put(fields: (String, natchez.TraceValue)*): F[Unit] =
      ref.update(_.append(lineage -> NatchezCommand.Put(fields.toList)))

    def attachError(err: Throwable, fields: (String, TraceValue)*): F[Unit] =
      ref.update(_.append(lineage -> NatchezCommand.AttachError(err, fields.toList)))

    def log(event: String): F[Unit] =
      ref.update(_.append(lineage -> NatchezCommand.LogEvent(event)))

    def log(fields: (String, TraceValue)*): F[Unit] =
      ref.update(_.append(lineage -> NatchezCommand.LogFields(fields.toList)))

    def kernel: F[Kernel] =
      ref.update(_.append(lineage -> NatchezCommand.AskKernel(k))).as(k)

    def makeSpan(name: String, options: Options): Resource[F, natchez.Span[F]] = {
      val acquire = ref
        .update(_.append(lineage -> NatchezCommand.CreateSpan(name, options.parentKernel, options)))
        .as(new Span(lineage / name, k, ref, options))

      val release = ref.update(_.append(lineage -> NatchezCommand.ReleaseSpan(name)))

      Resource.make(acquire)(_ => release)
    }

    def traceId: F[Option[String]] =
      ref.update(_.append(lineage -> NatchezCommand.AskTraceId)).as(None)

    def spanId: F[Option[String]] =
      ref.update(_.append(lineage -> NatchezCommand.AskSpanId)).as(None)

    def traceUri: F[Option[URI]] =
      ref.update(_.append(lineage -> NatchezCommand.AskTraceUri)).as(None)
  }

  class EntryPoint[F[_]: Applicative](val ref: Ref[F, Chain[(Lineage, NatchezCommand)]])
      extends natchez.EntryPoint[F] {

    override def root(name: String, options: natchez.Span.Options): Resource[F, Span[F]] =
      newSpan(name, Kernel(Map.empty), options)

    override def continue(
        name: String,
        kernel: Kernel,
        options: natchez.Span.Options
    ): Resource[F, Span[F]] =
      newSpan(name, kernel, options)

    override def continueOrElseRoot(
        name: String,
        kernel: Kernel,
        options: natchez.Span.Options
    ): Resource[F, Span[F]] =
      newSpan(name, kernel, options)

    private def newSpan(
        name: String,
        kernel: Kernel,
        options: natchez.Span.Options
    ): Resource[F, Span[F]] = {
      val acquire = ref
        .update(_.append(Lineage.Root -> NatchezCommand.CreateRootSpan(name, kernel, options)))
        .as(new Span(Lineage.Root(name), kernel, ref, options))

      val release = ref.update(_.append(Lineage.Root -> NatchezCommand.ReleaseRootSpan(name)))

      Resource.make(acquire)(_ => release)
    }
  }

  object EntryPoint {
    def create[F[_]: Concurrent]: F[EntryPoint[F]] =
      Ref.of[F, Chain[(Lineage, NatchezCommand)]](Chain.empty).map(log => new EntryPoint(log))
  }

  sealed trait Lineage {
    def /(name: String): Lineage.Child = Lineage.Child(name, this)
  }
  object Lineage {
    val defaultRootName = "root"
    case class Root(name: String) extends Lineage
    object Root extends Root(defaultRootName)
    final case class Child(name: String, parent: Lineage) extends Lineage
  }

  sealed trait NatchezCommand
  object NatchezCommand {
    case class AskKernel(kernel: Kernel) extends NatchezCommand
    case object AskSpanId extends NatchezCommand
    case object AskTraceId extends NatchezCommand
    case object AskTraceUri extends NatchezCommand
    case class Put(fields: List[(String, natchez.TraceValue)]) extends NatchezCommand
    case class CreateSpan(name: String, kernel: Option[Kernel], options: natchez.Span.Options)
        extends NatchezCommand
    case class ReleaseSpan(name: String) extends NatchezCommand
    case class AttachError(err: Throwable, fields: List[(String, TraceValue)])
        extends NatchezCommand
    case class LogEvent(event: String) extends NatchezCommand
    case class LogFields(fields: List[(String, TraceValue)]) extends NatchezCommand
    // entry point
    case class CreateRootSpan(name: String, kernel: Kernel, options: natchez.Span.Options)
        extends NatchezCommand
    case class ReleaseRootSpan(name: String) extends NatchezCommand
  }

}
