// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

class SpanPropagationTest extends InMemorySuite {

  traceTest(
    "propagation",
    new TraceTest {
      def program[F[_]: Trace] =
        Trace[F].span("parent")(Trace[F].span("child")(Trace[F].put("answer" -> 42)))

      def expectedHistory = List(
        (Lineage.Root, NatchezCommand.CreateRootSpan("root", Kernel(Map()))),
        (Lineage.Root, NatchezCommand.CreateSpan("parent", None)),
        (Lineage.Root / "parent", NatchezCommand.CreateSpan("child", None)),
        (Lineage.Root / "parent" / "child", NatchezCommand.Put(List("answer" -> 42))),
        (Lineage.Root / "parent", NatchezCommand.ReleaseSpan("child")),
        (Lineage.Root, NatchezCommand.ReleaseSpan("parent")),
        (Lineage.Root, NatchezCommand.ReleaseRootSpan("root"))
      )
    }
  )
}
