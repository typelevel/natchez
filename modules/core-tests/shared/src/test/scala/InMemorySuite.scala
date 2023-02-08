// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.arrow.FunctionK
import cats.{Applicative, ~>}
import cats.syntax.all._
import cats.data.Kleisli
import cats.effect.{Concurrent, IO, IOLocal, MonadCancelThrow}
import cats.mtl.Local
import munit.CatsEffectSuite
import natchez.InMemory.Lineage.defaultRootName
import natchez.mtl._

trait InMemorySuite extends CatsEffectSuite {
  type Lineage = InMemory.Lineage
  val Lineage = InMemory.Lineage
  type NatchezCommand = InMemory.NatchezCommand
  val NatchezCommand = InMemory.NatchezCommand
}

object InMemorySuite {
  trait TraceSuite extends InMemorySuite {
    trait TraceTest {
      def program[F[_]: MonadCancelThrow: Trace]: F[Unit]

      def expectedHistory: List[(Lineage, NatchezCommand)]
    }

    def traceTest(name: String, tt: TraceTest): Unit = {
      test(s"$name - Kleisli")(
        testTraceKleisli(tt.program[Kleisli[IO, Span[IO], *]](implicitly, _), tt.expectedHistory)
      )
      test(s"$name - IOLocal")(testTraceIoLocal(tt.program[IO](implicitly, _), tt.expectedHistory))
    }

    def testTraceKleisli(
        traceProgram: Trace[Kleisli[IO, Span[IO], *]] => Kleisli[IO, Span[IO], Unit],
        expectedHistory: List[(Lineage, NatchezCommand)]
    ): IO[Unit] = testTrace[Kleisli[IO, Span[IO], *]](
      traceProgram,
      root => IO.pure(Trace[Kleisli[IO, Span[IO], *]] -> (k => k.run(root))),
      expectedHistory
    )

    def testTraceIoLocal(
        traceProgram: Trace[IO] => IO[Unit],
        expectedHistory: List[(Lineage, NatchezCommand)]
    ): IO[Unit] = testTrace[IO](traceProgram, Trace.ioTrace(_).map(_ -> identity), expectedHistory)

    def testTrace[F[_]](
        traceProgram: Trace[F] => F[Unit],
        makeTraceAndResolver: Span[IO] => IO[(Trace[F], F[Unit] => IO[Unit])],
        expectedHistory: List[(Lineage, NatchezCommand)]
    ): IO[Unit] =
      InMemory.EntryPoint.create[IO].flatMap { ep =>
        val traced = ep.root(defaultRootName).use { r =>
          makeTraceAndResolver(r).flatMap { case (traceInstance, resolve) =>
            resolve(traceProgram(traceInstance))
          }
        }
        traced *> ep.ref.get.map { history =>
          assertEquals(history.toList, expectedHistory)
        }
      }
  }

  trait LocalSuite extends InMemorySuite {
    type LocalProgram[F[_]] = (EntryPoint[F], Local[F, Span[F]]) => F[Unit]

    trait LocalTest {
      def program[F[_]: MonadCancelThrow](entryPoint: EntryPoint[F])(implicit
          L: Local[F, Span[F]]
      ): F[Unit]
      def expectedHistory: List[(Lineage, NatchezCommand)]
    }

    def localTest(name: String, tt: LocalTest): Unit = {
      test(s"$name - Kleisli")(
        testLocalKleisli(tt.program[Kleisli[IO, Span[IO], *]](_)(implicitly, _), tt.expectedHistory)
      )
      test(s"$name - IOLocal")(
        testLocalIoLocal(tt.program[IO](_)(implicitly, _), tt.expectedHistory)
      )
    }

    private def testLocalKleisli(
        localProgram: LocalProgram[Kleisli[IO, Span[IO], *]],
        expectedHistory: List[(Lineage, NatchezCommand)]
    ): IO[Unit] = testProgramGivenEntryPoint[Kleisli[IO, Span[IO], *]](
      localProgram(_, implicitly),
      Kleisli.applyK(Span.noop[IO]),
      expectedHistory
    )

    private def testLocalIoLocal(
        localProgram: LocalProgram[IO],
        expectedHistory: List[(Lineage, NatchezCommand)]
    ): IO[Unit] =
      testProgramGivenEntryPoint[IO](
        ep =>
          IOLocal(Span.noop[IO])
            .map { ioLocal =>
              new Local[IO, Span[IO]] {
                override def local[A](fa: IO[A])(f: Span[IO] => Span[IO]): IO[A] =
                  ioLocal.get.flatMap { initial =>
                    ioLocal.set(f(initial)) >> fa.guarantee(ioLocal.set(initial))
                  }

                override def applicative: Applicative[IO] = implicitly

                override def ask[E2 >: Span[IO]]: IO[E2] = ioLocal.get
              }
            }
            .flatMap(localProgram(ep, _)),
        FunctionK.id,
        expectedHistory
      )

    private def testProgramGivenEntryPoint[F[_]: Concurrent](
        localProgram: EntryPoint[F] => F[Unit],
        fk: F ~> IO,
        expectedHistory: List[(Lineage, NatchezCommand)]
    ): IO[Unit] =
      fk(InMemory.EntryPoint.create[F].flatMap { ep =>
        localProgram(ep) *> ep.ref.get.map { history =>
          assertEquals(history.toList, expectedHistory)
        }
      })
  }

}
