// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package mtl

import cats.effect.{MonadCancelThrow, Trace => _}
import cats.mtl._
import natchez.InMemory.Lineage.defaultRootName

class LocalTraceSpec extends InMemorySuite.LocalSuite {

  private def useTrace[F[_]: Trace]: F[Unit] = Trace[F].log("hello world")

  localTest(
    "should compile with",
    new LocalTest {
      override def program[F[_]: MonadCancelThrow](implicit L: Local[F, Span[F]]): F[Unit] =
        useTrace[F]

      override def expectedHistory: List[(Lineage, NatchezCommand)] = List(
        Lineage.Root -> NatchezCommand
          .CreateRootSpan(defaultRootName, Kernel(Map()), Span.Options.Defaults),
        Lineage.Root(defaultRootName) -> NatchezCommand.LogEvent("hello world"),
        Lineage.Root -> NatchezCommand.ReleaseRootSpan(defaultRootName)
      )
    }
  )
}
