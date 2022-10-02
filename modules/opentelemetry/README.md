# OpenTelemetry

OpenTelemetry is capable of exporting to different collector types. Exporters are registered globally against a
singleton registry. There is nothing stopping someone registering a new exporter outside of a side effect, so it is up
to the user whether to do so inside the effects system or not.

The recommended approach for registering exporters is to use a resource. The snippet below shows how this may be done
for the Google Cloud Trace exporter implementation:

The google cloud trace export can be found here:

```scala
libraryDependencies ++= Seq(
  "com.google.cloud.opentelemetry" % "exporter-trace" % "0.20.0",
  "com.google.cloud" % "google-cloud-trace" % "2.1.9"
)
```

```scala
import cats.effect.{Resource, Sync}
import io.opentelemetry.api.GlobalOpenTelemetry
import natchez.opentelemetry.OpenTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.BatchSpanProcessor
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.{ContextPropagators, TextMapPropagator}
import com.google.cloud.opentelemetry.trace.{TraceConfiguration, TraceExporter}
import natchez.opentelemetry.OpenTelemetry

def entrypoint[F[_] : Sync](projectId: String)(configure: TraceConfiguration.Builder => TraceConfiguration.Builder): Resource[F, EntryPoint[F]] =
  Resource
    .make(
      Sync[F].delay(
        OpenTelemetrySdk
          .builder()
          .setTracerProvider(
            SdkTracerProvider
              .builder()
              .addSpanProcessor(
                BatchSpanProcessor
                  .builder(
                    TraceExporter
                      .createWithConfiguration(
                        configure(TraceConfiguration.builder().setProjectId(projectId)).build()
                      )
                  )
                  .build()
              )
              .build()
          ).setPropagators(
          ContextPropagators.create(
            TextMapPropagator.composite(W3CTraceContextPropagator.getInstance(), W3CBaggagePropagator.getInstance())
          )
        ).build()
      )
    )(sdk =>
      Sync[F].blocking {
        sdk.getSdkTracerProvider.close()
      }
    )
    .flatMap(sdk => Resource.eval(OpenTelemetry.entryPointForSdk[F](sdk)))

```
