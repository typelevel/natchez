import cats.data.Kleisli
import cats.effect._
import cats.implicits._
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.resources.{Resource => OtelResource}
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.BatchSpanProcessor
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes
import natchez.{EntryPoint, Span, Trace}
import natchez.opentelemetry.OpenTelemetry

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.DurationInt

// change this into an object if you'd like to run it
class OpenTelemetryExample extends IOApp {
  def entryPoint[F[_]: Async]: Resource[F, EntryPoint[F]] =
    for {
      exporter <- OpenTelemetry.lift(
        "OtlpGrpcSpanExporter",
        Sync[F].delay {
          OtlpGrpcSpanExporter.builder()
            .setEndpoint("http://localhost:4317")
            .build()
        }
      )
      processor <- OpenTelemetry.lift(
        "BatchSpanProcessor",
        Sync[F].delay {
          BatchSpanProcessor.builder(exporter).build()
        }
      )
      tracer <- OpenTelemetry.lift(
        "Tracer",
        Sync[F].delay {
          SdkTracerProvider.builder()
            .setResource(
              OtelResource.create(
                Attributes.of(ResourceAttributes.SERVICE_NAME, "OpenTelemetryExample")
              )
            )
            .addSpanProcessor(processor)
            .build()
        }
      )
      ep <- OpenTelemetry.entryPoint(globallyRegister = true) { builder =>
        Resource.eval(Sync[F].delay {
          builder
            .setTracerProvider(tracer)
            .setPropagators(
              ContextPropagators.create(W3CTraceContextPropagator.getInstance())
            )
        }
      )}
    } yield ep

  override def run(args: List[String]): IO[ExitCode] =
    entryPoint[IO].use { ep =>
      ep.root("root span").use { span =>
        span.put("service.name" -> "natchez opentelemetry example") *>
          program[Kleisli[IO, Span[IO], *]].apply(span).as(ExitCode.Success)
      }
    }

  def program[F[_]: Sync: Trace: Timer]: F[Unit] =
    Trace[F].traceId.flatTap(tid => Sync[F].delay { println(s"did some work with traceid of $tid") }) *>
      Trace[F].span("outer span") {
        Trace[F].put("foo" -> "bar") *>
          (Trace[F].span("first thing") {
            Timer[F].sleep(2.seconds)
          },
            Trace[F].span("second thing") {
              Timer[F].sleep(2.seconds)
            }).tupled
      }.void
}
