// Copyright (c) 2019-2021 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import cats._
import cats.data.Kleisli
import cats.effect.{Trace => _, _}
import cats.syntax.all._
import natchez._
import scala.util.Random
import scala.concurrent.duration._
import java.net.URI


object GlobalTracerMain extends IOApp {

  def runF[F[_]: Async: Trace: Parallel]: F[Unit] =
    Trace[F].span("Sort some stuff!") {
      for {
        as <- Sync[F].delay(List.fill(10)(Random.nextInt(1000)))
        _  <- Sort.qsort[F, Int](as)
        u  <- Trace[F].traceUri
        _  <- u.traverse(uri => Sync[F].delay(println(s"View this trace at $uri")))
        _  <- Sync[F].delay(println("Done."))
      } yield ()
    }

  def globalTracerEntryPoint[F[_]: Sync]: F[Option[EntryPoint[F]]] = {
    // Datadog
    // import natchez.datadog.DDTracer
    // val prefix = Some(new URI("https://app.datadoghq.com")) // https://app.datadoghq.eu for Europe
    // DDTracer.globalTracerEntryPoint[F](prefix)

    // Jaeger
    import natchez.jaeger.Jaeger
    val prefix = Some(new URI("http://localhost:16686"))
    Jaeger.globalTracerEntryPoint[F](prefix)

    // Lightstep
    // import natchez.lightstep.Lightstep
    // Lightstep.globalTracerEntryPoint[F]

  }

  def run(args: List[String]): IO[ExitCode] = {
    globalTracerEntryPoint[IO].flatMap {
      case None => IO.delay {
        println("No tracer registered to the global tracer.  Is your agent attached with tracing enabled?")
      } as ExitCode.Error
      case Some(ep) =>
        ep.root("this is the root span")
          .use(span => runF[Kleisli[IO, Span[IO], *]].run(span)) *>
          IO.sleep(1.second) as ExitCode.Success // Turns out Tracer.close() in Jaeger doesn't block. Annoying. Maybe fix in there?
    }
  }


}
