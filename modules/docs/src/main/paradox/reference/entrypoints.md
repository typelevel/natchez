
# Entry Points

An `EntryPoint` provides a way to create a root `Span`, or continue a `Span` that began on another computer. In practice this means that an entry point is a live connection to some tracing back-end. This is where you start: typically `IOApp.run` will construct an entry point that you use for the life of your program.

![](../entrypoint.svg)

You can obtain an `EntryPoint` through a vendor-specific factory. See the list of @ref:[back ends](../backends/index.md) and select the one you'd like to use.

## Creating an Initial Span

A value `EntryPoint[F]` provides three methods for constructing an initial span resource.

- `root` creates a new top-level span resource with no parent. This kind of initial span is typically used for endpoints on the public surface of a system.
- `continue` creates a span resource wih a parent specified by a provided `Kernel` (typically received in http headers). If the kernel is invalid (this is determined in a ) an error is raised in `F`. This kind of initial span is typically used for endpoints of internal services that are always invoked by higher-level services.
- `continueOrElseRoot` attempts to `continue` and on failure (if the kernel is invalid) it creates a new `root`. This is often a safe bet if it's not clear whether you will always (or never) receive an incoming kernel.

## Http4s Example

Given an `EntryPoint` we can trace an Http4s request by constructing a new root span in the handler.

```scala mdoc
import cats.effect.IO
import org.http4s.dsl.io._
import org.http4s.HttpRoutes
import natchez.EntryPoint

def routes(ep: EntryPoint[IO]): HttpRoutes[IO] =
  HttpRoutes.of[IO] {
    case GET -> Root / "hello" / name =>
      ep.root("hello").use { span =>
        span.put("the-name" -> name) *>
        Ok(s"Hello, $name.")
      }
  }
```

We can also attempt to continue a trace started on another computer by constructing a `Kernel` from our header values

```scala mdoc
import natchez.Kernel

def continuedRoutes(ep: EntryPoint[IO]): HttpRoutes[IO] =
  HttpRoutes.of[IO] {
    case req@(GET -> Root / "hello" / name) =>

      val k: Kernel =
        Kernel(req.headers.toList.map { h => h.name.value -> h.value }.toMap)

      ep.continueOrElseRoot("hello", k).use { span =>
        span.put("the-name" -> name) *>
        Ok(s"Hello, $name.")
      }

  }
```

@@@note
A support package for http4s will make these operations transparent via a middleware, but it's still useful to understand the low-level operations.
@@@

