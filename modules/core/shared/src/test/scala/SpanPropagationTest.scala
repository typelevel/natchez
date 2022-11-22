// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.data.Kleisli
import cats.effect.IO
import munit.CatsEffectSuite

import InMemory.{Lineage, NatchezCommand}

class SpanPropagationTest extends CatsEffectSuite {
  def prg[F[_]: Trace] =
    Trace[F].span("parent")(Trace[F].span("child")(Trace[F].put("answer" -> 42)))

  def testPropagation[F[_]](f: Span[IO] => IO[(Trace[F], F[Unit] => IO[Unit])]) = {
    InMemory.EntryPoint.create.flatMap { ep =>
      val traced = ep.root("root").use { r =>
        f(r).flatMap { case (traceInstance, resolve) =>
          resolve(prg(traceInstance))
        }
      }
      traced *> ep.ref.get.map { history =>
        assertEquals(
          history.toList,
          List(
            (Lineage.Root, NatchezCommand.CreateRootSpan("root", Kernel(Map()))),
            (Lineage.Root, NatchezCommand.CreateSpan("parent", None)),
            (Lineage.Root / "parent", NatchezCommand.CreateSpan("child", None)),
            (Lineage.Root / "parent" / "child", NatchezCommand.Put(List("answer" -> 42))),
            (Lineage.Root / "parent", NatchezCommand.ReleaseSpan("child")),
            (Lineage.Root, NatchezCommand.ReleaseSpan("parent")),
            (Lineage.Root, NatchezCommand.ReleaseRootSpan("root"))
          )
        )
      }
    }
  }

 
  test("kleisli") {
    testPropagation[Kleisli[IO, Span[IO], *]](root => IO.pure(Trace[Kleisli[IO, Span[IO], *]] -> (k => k.run(root))))
  }
  
  test("io") {
    testPropagation[IO](root => Trace.ioTrace(root).map(_ -> identity))
  }
}
