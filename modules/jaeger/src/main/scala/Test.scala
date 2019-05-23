package example

import cats.data.Kleisli
import cats.effect._
import cats.implicits._
import io.jaegertracing.Configuration
import io.jaegertracing.Configuration._
import natchez.Span
import natchez.Trace
import natchez.Tracer
import skunk._
import skunk.codec.all._
import skunk.implicits._

object Main extends IOApp {

  // A tracer built from an openTracing implementation, in this case Jaeger.
  def tracer[F[_]: Sync]: Resource[F, Tracer[F]] =
    Resource.make(Sync[F].delay(
      new Configuration("natchez-example")
        .withSampler(SamplerConfiguration.fromEnv)
        .withReporter(ReporterConfiguration.fromEnv)
        .getTracer
    ))(t => Sync[F].delay(t.close))
      .map(Tracer.fromOpenTracing(_))

  // A skunk session resource
  def session[F[_]: Concurrent: ContextShift]: Resource[F, Session[F]] =
    Session.single(
      host     = "localhost",
      user     = "postgres",
      database = "world"
    )

  def names[F[_]: Trace](s: Session[F]): F[List[String]] =
    Trace[F].span("names") {
      s.execute(sql"select name from country where population < 200000".query(varchar))
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
    tracer[IO].use { t =>
      t.root("root").use { span =>
        runF[Kleisli[IO, Span[IO], ?]].run(span)
      }
    }

  }
