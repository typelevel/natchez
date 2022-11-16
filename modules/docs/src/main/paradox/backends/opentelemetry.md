# OpenTelemetry

The `natchez-opentelemetry` module provides a backend that uses [OpenTelemetry](https://opentelemetry.io) to report spans.

To use it, add the following dependency.

@@dependency[sbt,Maven,Gradle] {
group="$org$"
artifact="natchez-opentelemetry-2.13"
version="$version$"
}

Then add any exporter, for example:

@@dependency[sbt,Maven,Gradle] {
group="io.opentelemetry"
artifact="opentelemetry-exporter-otlp"
version="1.12.0"
}

## Configuring an OpenTelemetry entrypoint

There are two methods you'll need to construct an `OpenTelemetry` `EndPoint`.

`OpenTelemetry.lift` is used to turn an `F[_]` that constructs a `SpanExporter`, `SpanProcessor` or `SdkTraceProvider` into a `Resource` that will shut it down cleanly.
This takes a `String` of what you've constructed, so we can give a nice error if it fails to shut down cleanly.

The `OpenTelemetry.entryPoint` method takes a boolean called `globallyRegister` which tells it whether to register this `OpenTelemetry` globally. This may be helpful if you have other Java dependencies that use the global tracer. It defaults to false.

It also takes an `OpenTelemetrySdkBuilder => Resource[F, OpenTelemetrySdkBuilder]` so that you can configure the Sdk.

Here's an example of configuring one with the `otlp` exporter with batch span processing:

```scala mdoc:passthrough
import natchez.EntryPoint
import natchez.opentelemetry.OpenTelemetry
import cats.effect._
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes
import io.opentelemetry.sdk.resources.{Resource => OtelResource}
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.BatchSpanProcessor

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
```
