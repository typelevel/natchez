# Datadog

To get access to an `EntryPoint` instance for DataDog, add the following dependency.

It transitively pulls in `"com.datadoghq" % "dd-trace-ot"` and `"com.datadoghq" % "dd-trace-api"`.

@@dependency[sbt,Maven,Gradle] {
  group="$org$"
  artifact="natchez-datadog_2.13"
  version="$version$"
}

## Constructing an EntryPoint

You can use `DDTracer.entryPoint` to build a DataDog tracer, register it globally, and get it as a `Resource[F, EntryPoint[F]]`.
The method takes a function in which you can customize the underlying `DDTracerBuilder` instance to specify the service name, sampling rules, etc.

At the end, the function should return a `datadog.opentracing.DDTracer`, for example by calling `build()`.

The builder is mutable, so customizing it is considered an effect - hence the wrapping in `Sync[F].delay`:

```scala mdoc
import cats.effect.{Sync, Resource}
import natchez.datadog.DDTracer
import natchez.EntryPoint

def entryPoint[F[_]: Sync]: Resource[F, EntryPoint[F]] =
  DDTracer.entryPoint[F] {
    builder => Sync[F].delay {
      builder
        .serviceName("my-app")
        .build()
    }
  }
```

An alternative approach is to use the already registered global tracer (for example, if you've already set it up and registered through some other means, like adding the Java Agent):

```scala mdoc
import java.net.URI

// Will return `None` if there's no tracer registered already
def entryPointUseGlobal[F[_]: Sync]: F[Option[EntryPoint[F]]] =
  DDTracer.globalTracerEntryPoint[F](Some(new URI(s"https://app.datadoghq.com")))
```
