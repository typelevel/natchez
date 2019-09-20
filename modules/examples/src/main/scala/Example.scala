// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import cats._
import cats.data.Kleisli
import cats.effect._
import cats.implicits._
import natchez._
import skunk._
import skunk.codec.all._
import skunk.implicits._
import natchez.log.Log
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

object Main extends IOApp {

  // A skunk session resource
  def session[F[_]: Concurrent: ContextShift: Trace]: Resource[F, Session[F]] =
    Session.single(
      host     = "localhost",
      user     = "postgres",
      database = "world"
    )

  def names[F[_]: FlatMap: Trace](s: Session[F]): F[List[String]] =
    Trace[F].span("names") {
      for {
        ss <- s.execute(sql"select name from country where population < 200000".query(varchar))
        _  <- Trace[F].put("rows-count" -> ss.length)
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
            _  <- Trace[F].put("in-between" -> "yay!")
            _  <- names(s)
            m  <- Trace[F].kernel
            _  <- Sync[F].delay(m.toHeaders.foreach(println))
          } yield ()
        }
      }
    }

  // For Honeycomb you would say
  // def entryPoint[F[_]: Sync]: Resource[F, EntryPoint[F]] =
  //   Honeycomb.entryPoint[F]("natchez-example") { ob =>
  //     Sync[F].delay {
  //       ob.setWriteKey("<your API key here>")
  //         .setDataset("<your dataset>")
  //         .build
  //     }
  //   }

  // The following would be the minimal entrypoint setup for Lighstep. Note that
  // by default examples project uses lighstep HTTP binding. To change that,
  // edit the project dependencies.
  // def entryPoint[F[_]: Sync]: Resource[F, EntryPoint[F]] =
  //   Lightstep.entryPoint[F] { ob =>
  //     Sync[F].delay {
  //       val options = ob
  //         .withAccessToken("<your access token>")
  //         .withComponentName("<your app's name>")
  //         .withCollectorHost("<your collector host>")
  //         .withCollectorProtocol("<your collector protocol>")
  //         .withCollectorPort(<your collector port>)
  //         .build()
  //
  //       new JRETracer(options)
  //     }
  //   }

  // Jaeger
  // def entryPoint[F[_]: Sync]: Resource[F, EntryPoint[F]] =
  //   Jaeger.entryPoint[F]("natchez-example") { c =>
  //     Sync[F].delay {
  //       c.withSampler(SamplerConfiguration.fromEnv)
  //        .withReporter(ReporterConfiguration.fromEnv)
  //        .getTracer
  //     }
  //   }

  def entryPoint[F[_]: Sync: Logger]: Resource[F, EntryPoint[F]] =
    Log.entryPoint[F]("foo").pure[Resource[F, ?]]

  def run(args: List[String]): IO[ExitCode] = {
    implicit val log = Slf4jLogger.getLogger[IO]
    entryPoint[IO].use { ep =>
      ep.root("root").use { span =>
        runF[Kleisli[IO, Span[IO], ?]].run(span)
      }
    } as ExitCode.Success
  }

}


