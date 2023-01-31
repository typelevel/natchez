// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.effect.MonadCancelThrow

class SpanCoalesceTest extends InMemorySuite.TraceSuite {

  traceTest(
    "suppress - nominal",
    new TraceTest {
      def program[F[_]: MonadCancelThrow: Trace] = {
        def detailed =
          Trace[F].span("parent")(Trace[F].span("child")(Trace[F].put("answer" -> 42)))
        Trace[F].span("suppressed", Span.Options.Suppress)(detailed)
      }

      def expectedHistory = List(
        (Lineage.Root, NatchezCommand.CreateRootSpan("root", Kernel(Map()), Span.Options.Defaults)),
        (Lineage.Root, NatchezCommand.CreateSpan("suppressed", None, Span.Options.Suppress)),
        (Lineage.Root, NatchezCommand.ReleaseSpan("suppressed")),
        (Lineage.Root, NatchezCommand.ReleaseRootSpan("root"))
      )
    }
  )

  traceTest(
    "coaslesce - nominal",
    new TraceTest {
      def program[F[_]: MonadCancelThrow: Trace] = {
        def detailed =
          Trace[F].span("parent")(Trace[F].span("child")(Trace[F].put("answer" -> 42)))
        Trace[F].span("coalesced", Span.Options.Coalesce)(detailed)
      }

      def expectedHistory = List(
        (Lineage.Root, NatchezCommand.CreateRootSpan("root", Kernel(Map()), Span.Options.Defaults)),
        (Lineage.Root, NatchezCommand.CreateSpan("coalesced", None, Span.Options.Coalesce)),
        (Lineage.Root / "coalesced", NatchezCommand.Put(List("answer" -> 42))),
        (Lineage.Root, NatchezCommand.ReleaseSpan("coalesced")),
        (Lineage.Root, NatchezCommand.ReleaseRootSpan("root"))
      )
    }
  )
}
