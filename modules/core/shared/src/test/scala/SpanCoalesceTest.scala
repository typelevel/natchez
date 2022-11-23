// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.data.Kleisli
import cats.effect.IO
import munit.CatsEffectSuite

import InMemory.{Lineage, NatchezCommand}

class SpanCoalesceTest extends CatsEffectSuite {

  test("suppress - nominal") {
    def detailed[F[_]: Trace] =
      Trace[F].span("parent")(Trace[F].span("child")(Trace[F].put("answer" -> 42)))
    def suppressed[F[_]: Trace] =
      Trace[F].span("suppressed", Span.Options.Suppress)(detailed[F])

    testTraceKleisli(_ => suppressed[Kleisli[IO, Span[IO], *]], List(
      (Lineage.Root, NatchezCommand.CreateRootSpan("root", Kernel(Map()))),
      (Lineage.Root, NatchezCommand.CreateSpan("suppressed", None)),
      (Lineage.Root, NatchezCommand.ReleaseSpan("suppressed")),
      (Lineage.Root, NatchezCommand.ReleaseRootSpan("root"))
    ))
  }

  test("coaslesce - nominal") {
    def detailed[F[_]: Trace] =
      Trace[F].span("parent")(Trace[F].span("child")(Trace[F].put("answer" -> 42)))
    def coaslesced[F[_]: Trace] =
      Trace[F].span("coalesced", Span.Options.Coalesce)(detailed[F])

    testTraceKleisli(_ => coaslesced[Kleisli[IO, Span[IO], *]], List(
      (Lineage.Root, NatchezCommand.CreateRootSpan("root", Kernel(Map()))),
      (Lineage.Root, NatchezCommand.CreateSpan("coalesced", None)),
      (Lineage.Root / "coalesced", NatchezCommand.Put(List("answer" -> 42))),
      (Lineage.Root, NatchezCommand.ReleaseSpan("coalesced")),
      (Lineage.Root, NatchezCommand.ReleaseRootSpan("root"))
    ))
  }

  def testTraceKleisli(
    traceProgram: Trace[Kleisli[IO, Span[IO], *]] => Kleisli[IO, Span[IO], Unit],
    expectedHistory: List[(Lineage, NatchezCommand)]
  ) = testTrace[Kleisli[IO, Span[IO], *]](traceProgram, root =>
      IO.pure(Trace[Kleisli[IO, Span[IO], *]] -> (k => k.run(root)))
     , expectedHistory)

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