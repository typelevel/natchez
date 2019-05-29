// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import cats._
import cats.data.Kleisli
import cats.effect._
import cats.implicits._
import natchez._, natchez.implicits._
import skunk._
import skunk.codec.all._
import skunk.implicits._
import natchez.jaeger.JaegerTracer

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
        _  <- Trace[F].log("rows-count" -> ss.length)
      } yield ss
    }

  def printNames[F[_]: Sync: Trace](ns: List[String]): F[Unit] =
    Trace[F].span("printNames") {
      ns.traverse_(n => Sync[F].delay(println(n)))
    }

  def runF[F[_]: Trace: Concurrent: ContextShift: Timer]: F[Unit] =
    Trace[F].span("session") {
      session[F].use { s =>
        Trace[F].span("try-again") {
          for {
            _  <- names(s)
            _  <- Trace[F].log("in-between" -> "yay!")
            _  <- names(s)
            _  <- Trace[F].setBaggageItem("foo", "❤️")
            m  <- Trace[F].httpHeaders
            _  <- Sync[F].delay(m.foreach(println))
          } yield ()
        }
      }
    }

  def tracer[F[_]: Sync]: Resource[F, Tracer[F]] =
    JaegerTracer.fromEnv("natchez-example")

  def run(args: List[String]): IO[ExitCode] =
    tracer[IO].use { t =>
      t.root("root").use { span =>
        runF[Kleisli[IO, Span[IO], ?]].run(span) // now with a real tracer
      } .replicateA(3)
    } as ExitCode.Success

}


