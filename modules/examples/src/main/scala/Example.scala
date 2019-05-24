// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import cats._
import cats.data.Kleisli
import cats.effect._
import cats.implicits._
import natchez._, natchez.implicits._
import natchez.jaeger.JaegerTracer
import skunk._
import skunk.codec.all._
import skunk.implicits._

object Main extends IOApp {

  // A skunk session resource
  def session[F[_]: Concurrent: ContextShift]: Resource[F, Session[F]] =
    Session.single(
      host     = "localhost",
      user     = "postgres",
      database = "world"
    )

  def names[F[_]: FlatMap: Trace](s: Session[F]): F[List[String]] =
    Trace[F].span("names") {
      for {
        ss <- s.execute(sql"select name from country where population < 200000".query(varchar))
        _  <- Trace[F].setTag("rows", ss.length)
      } yield ss
    }

  def printNames[F[_]: Sync: Trace](ns: List[String]): F[Unit] =
    Trace[F].span("printNames") {
      ns.traverse_(n => Sync[F].delay(println(n)))
    }

  def runF[F[_]: Trace: Concurrent: ContextShift]: F[ExitCode] =
    session[F].use { s =>
      Trace[F].span("runF") {
        for {
          ns <- names(s)
          _  <- printNames[F](ns)
        } yield ExitCode.Success
      }
    }

  def run(args: List[String]): IO[ExitCode] =
    runF[IO] *> // first with the free no-op tracer
    JaegerTracer.fromEnv[IO]("natchez-example").use { t =>
      t.root("root").use { span =>
        runF[Kleisli[IO, Span[IO], ?]].run(span) // now with a real tracer
      }
    }

}


