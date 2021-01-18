// Copyright (c) 2019-2021 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

import cats._
import cats.data.Kleisli
import cats.effect._
import cats.implicits._
import com.newrelic.telemetry.SimpleSpanBatchSender
import natchez._
import natchez.newrelic.NewRelic

import scala.util.Random
import scala.concurrent.duration._

object NewrelicExample extends IOApp {

  // Intentionally slow parallel quicksort, to demonstrate branching. If we run too quickly it seems
  // to break Jaeger with "skipping clock skew adjustment" so let's pause a bit each time.
  def qsort[F[_]: Monad: Parallel: Trace: Timer, A: Order](as: List[A]): F[List[A]] =
    Trace[F].span(as.mkString(",")) {
      Timer[F].sleep(10.milli) *> {
        as match {
          case Nil => Monad[F].pure(Nil)
          case h :: t =>
            val (a, b) = t.partition(_ <= h)
            (qsort[F, A](a), qsort[F, A](b)).parMapN(_ ++ List(h) ++ _)
        }
      }
    }

  def runF[F[_]: Sync: Trace: Parallel: Timer]: F[Unit] =
    Trace[F].span("Sort some stuff!") {
      for {
        as <- Sync[F].delay(List.fill(100)(Random.nextInt(1000)))
        _  <- qsort[F, Int](as)
      } yield ()
    }

  // Jaeger
  def entryPoint[F[_]: Sync](apiKey: String): EntryPoint[F] =
    NewRelic.entryPoint[F]("qsort_example") {

      SimpleSpanBatchSender
        .builder(apiKey, java.time.Duration.ofSeconds(5))
        .enableAuditLogging()
        .build()
    }

  def run(args: List[String]): IO[ExitCode] =
    ciris
      .env("NR_API_KEY")
      .load[IO]
      .flatMap(
        apiKey =>
          entryPoint[IO](apiKey)
            .root("this is the root span")
            .use(span => runF[Kleisli[IO, Span[IO], *]].run(span))
            .as(ExitCode.Success))

}
