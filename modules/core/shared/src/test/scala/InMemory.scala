// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import java.net.URI

import cats.data.{Chain, Kleisli}
import cats.effect.{IO, MonadCancelThrow, Ref, Resource}

import natchez.Span.Options
import munit.CatsEffectSuite

object InMemory {

  class Span(
      lineage: Lineage,
      k: Kernel,
      ref: Ref[IO, Chain[(Lineage, NatchezCommand)]],
      val options: Options
  ) extends natchez.Span.Default[IO] {
    override protected val spanCreationPolicy: Options.SpanCreationPolicy =
      options.spanCreationPolicy

    def put(fields: (String, natchez.TraceValue)*): IO[Unit] =
      ref.update(_.append(lineage -> NatchezCommand.Put(fields.toList)))

    def attachError(err: Throwable): IO[Unit] =
      ref.update(_.append(lineage -> NatchezCommand.AttachError(err)))

    def log(event: String): IO[Unit] =
      ref.update(_.append(lineage -> NatchezCommand.LogEvent(event)))

    def log(fields: (String, TraceValue)*): IO[Unit] =
      ref.update(_.append(lineage -> NatchezCommand.LogFields(fields.toList)))

    def kernel: IO[Kernel] =
      ref.update(_.append(lineage -> NatchezCommand.AskKernel(k))).as(k)

    def makeSpan(name: String, options: Options): Resource[IO, natchez.Span[IO]] = {
      val acquire = ref
        .update(_.append(lineage -> NatchezCommand.CreateSpan(name, options.parentKernel, options)))
        .as(new Span(lineage / name, k, ref, options))

      val release = ref.update(_.append(lineage -> NatchezCommand.ReleaseSpan(name)))

      Resource.make(acquire)(_ => release)
    }

    def traceId: IO[Option[String]] =
      ref.update(_.append(lineage -> NatchezCommand.AskTraceId)).as(None)

    def spanId: IO[Option[String]] =
      ref.update(_.append(lineage -> NatchezCommand.AskSpanId)).as(None)

    def traceUri: IO[Option[URI]] =
      ref.update(_.append(lineage -> NatchezCommand.AskTraceUri)).as(None)
  }

  class EntryPoint(val ref: Ref[IO, Chain[(Lineage, NatchezCommand)]])
      extends natchez.EntryPoint[IO] {

    override def root(name: String, options: natchez.Span.Options): Resource[IO, Span] =
      newSpan(name, Kernel(Map.empty), options)

    override def continue(
        name: String,
        kernel: Kernel,
        options: natchez.Span.Options
    ): Resource[IO, Span] =
      newSpan(name, kernel, options)

    override def continueOrElseRoot(
        name: String,
        kernel: Kernel,
        options: natchez.Span.Options
    ): Resource[IO, Span] =
      newSpan(name, kernel, options)

    private def newSpan(
        name: String,
        kernel: Kernel,
        options: natchez.Span.Options
    ): Resource[IO, Span] = {
      val acquire = ref
        .update(_.append(Lineage.Root -> NatchezCommand.CreateRootSpan(name, kernel, options)))
        .as(new Span(Lineage.Root, kernel, ref, options))

      val release = ref.update(_.append(Lineage.Root -> NatchezCommand.ReleaseRootSpan(name)))

      Resource.make(acquire)(_ => release)
    }
  }

  object EntryPoint {
    def create: IO[EntryPoint] =
      Ref.of[IO, Chain[(Lineage, NatchezCommand)]](Chain.empty).map(log => new EntryPoint(log))
  }

  sealed trait Lineage {
    def /(name: String): Lineage.Child = Lineage.Child(name, this)
  }
  object Lineage {
    case object Root extends Lineage
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
    case class AttachError(err: Throwable) extends NatchezCommand
    case class LogEvent(event: String) extends NatchezCommand
    case class LogFields(fields: List[(String, TraceValue)]) extends NatchezCommand
    // entry point
    case class CreateRootSpan(name: String, kernel: Kernel, options: natchez.Span.Options)
        extends NatchezCommand
    case class ReleaseRootSpan(name: String) extends NatchezCommand
  }

}

trait InMemorySuite extends CatsEffectSuite {
  type Lineage = InMemory.Lineage
  val Lineage = InMemory.Lineage
  type NatchezCommand = InMemory.NatchezCommand
  val NatchezCommand = InMemory.NatchezCommand

  trait TraceTest {
    def program[F[_]: MonadCancelThrow: Trace]: F[Unit]
    def expectedHistory: List[(Lineage, NatchezCommand)]
  }

  def traceTest(name: String, tt: TraceTest) = {
    test(s"$name - Kleisli")(
      testTraceKleisli(tt.program[Kleisli[IO, Span[IO], *]](implicitly, _), tt.expectedHistory)
    )
    test(s"$name - IOLocal")(testTraceIoLocal(tt.program[IO](implicitly, _), tt.expectedHistory))
  }

  def testTraceKleisli(
      traceProgram: Trace[Kleisli[IO, Span[IO], *]] => Kleisli[IO, Span[IO], Unit],
      expectedHistory: List[(Lineage, NatchezCommand)]
  ) = testTrace[Kleisli[IO, Span[IO], *]](
    traceProgram,
    root => IO.pure(Trace[Kleisli[IO, Span[IO], *]] -> (k => k.run(root))),
    expectedHistory
  )

  def testTraceIoLocal(
      traceProgram: Trace[IO] => IO[Unit],
      expectedHistory: List[(Lineage, NatchezCommand)]
  ) = testTrace[IO](traceProgram, Trace.ioTrace(_).map(_ -> identity), expectedHistory)

  def testTrace[F[_]](
      traceProgram: Trace[F] => F[Unit],
      makeTraceAndResolver: Span[IO] => IO[(Trace[F], F[Unit] => IO[Unit])],
      expectedHistory: List[(Lineage, NatchezCommand)]
  ) =
    InMemory.EntryPoint.create.flatMap { ep =>
      val traced = ep.root("root").use { r =>
        makeTraceAndResolver(r).flatMap { case (traceInstance, resolve) =>
          resolve(traceProgram(traceInstance))
        }
      }
      traced *> ep.ref.get.map { history =>
        assertEquals(history.toList, expectedHistory)
      }
    }
}
