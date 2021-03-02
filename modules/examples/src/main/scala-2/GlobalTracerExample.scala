// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import io.opentracing.Tracer

import cats._
import cats.data.Kleisli
import cats.effect._
import cats.syntax.all._
import natchez._
import scala.util.Random
import scala.concurrent.duration._
import java.net.URI


object GlobalTracerMain extends IOApp {

  def runF[F[_]: Sync: Trace: Parallel: Timer]: F[Unit] =
    Trace[F].span("Sort some stuff!") {
      for {
        as <- Sync[F].delay(List.fill(10)(Random.nextInt(1000)))
        _  <- Sort.qsort[F, Int](as)
        u  <- Trace[F].traceUri
        _  <- u.traverse(uri => Sync[F].delay(println(s"View this trace at $uri")))
        _  <- Sync[F].delay(println("Done."))
      } yield ()
    }

//DataDog
  val prefix = Some(new URI("https://app.datadoghq.com")) // https://app.datadoghq.eu for Europe

  def datadogEntryPoint[F[_]: Sync]: Resource[F, EntryPoint[F]] = {
    import java.util.Properties
    import natchez.datadog.DDTracer
    DDTracer.entryPoint[F](
      buildFunc =
        builder =>
          Sync[F].delay(
            builder.withProperties(
              new Properties() {
                put("writer.type", "LoggingWriter")
              }
            ).serviceName("natchez-sample").build()
          ),
      uriPrefix = prefix)   
  }


  // If a tracer has been registered to the global tracer use that,
  // otherwise use the provided fallback.
  def globalTracerEntryPoint[F[_]: Sync](makeEntryPoint: Tracer => F[EntryPoint[F]])(fallbackEntryPoint: Resource[F, EntryPoint[F]]) : Resource[F, EntryPoint[F]] = {
    import natchez.opentracing.GlobalTracer
    val maybeGlobalTracerEntryPoint = 
      GlobalTracer.createEntryPoint[F](makeEntryPoint)

    Resource.liftF(maybeGlobalTracerEntryPoint).flatMap {
      case None => fallbackEntryPoint
      case Some(ep) => Resource.pure[F, EntryPoint[F]](ep)
    }
  }
  
  def run(args: List[String]): IO[ExitCode] = {
    import natchez.datadog.DDEntryPoint
    globalTracerEntryPoint[IO](t => IO(new DDEntryPoint[IO](t, prefix)))(datadogEntryPoint).use { ep =>
      ep.root("this is the root span").use { span =>
        runF[Kleisli[IO, Span[IO], *]].run(span)
      } *> IO.sleep(1.second) // Turns out Tracer.close() in Jaeger doesn't block. Annoying. Maybe fix in there?
    } as ExitCode.Success
  }


}
