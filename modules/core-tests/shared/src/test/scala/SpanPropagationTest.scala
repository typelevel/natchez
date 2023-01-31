// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.effect.MonadCancelThrow
import cats.syntax.all._

class SpanPropagationTest extends InMemorySuite.TraceSuite {

  traceTest(
    "propagation",
    new TraceTest {
      def program[F[_]: MonadCancelThrow: Trace] =
        Trace[F].span("parent")(Trace[F].span("child")(Trace[F].put("answer" -> 42)))

      def expectedHistory = List(
        (Lineage.Root, NatchezCommand.CreateRootSpan("root", Kernel(Map()), Span.Options.Defaults)),
        (Lineage.Root, NatchezCommand.CreateSpan("parent", None, Span.Options.Defaults)),
        (Lineage.Root / "parent", NatchezCommand.CreateSpan("child", None, Span.Options.Defaults)),
        (Lineage.Root / "parent" / "child", NatchezCommand.Put(List("answer" -> 42))),
        (Lineage.Root / "parent", NatchezCommand.ReleaseSpan("child")),
        (Lineage.Root, NatchezCommand.ReleaseSpan("parent")),
        (Lineage.Root, NatchezCommand.ReleaseRootSpan("root"))
      )
    }
  )

  traceTest(
    "spanR",
    new TraceTest {
      def program[F[_]: MonadCancelThrow: Trace] =
        Trace[F].spanR("spanR").use { f =>
          Trace[F].span("span")(
            f(Trace[F].put("question" -> "ultimate")) *> Trace[F].put("answer" -> 42)
          )
        }

      def expectedHistory = List(
        (Lineage.Root, NatchezCommand.CreateRootSpan("root", Kernel(Map()), Span.Options.Defaults)),
        (Lineage.Root, NatchezCommand.CreateSpan("spanR", None, Span.Options.Defaults)),
        (Lineage.Root, NatchezCommand.CreateSpan("span", None, Span.Options.Defaults)),
        (Lineage.Root / "spanR", NatchezCommand.Put(List("question" -> "ultimate"))),
        (Lineage.Root / "span", NatchezCommand.Put(List("answer" -> 42))),
        (Lineage.Root, NatchezCommand.ReleaseSpan("span")),
        (Lineage.Root, NatchezCommand.ReleaseSpan("spanR")),
        (Lineage.Root, NatchezCommand.ReleaseRootSpan("root"))
      )
    }
  )
}
