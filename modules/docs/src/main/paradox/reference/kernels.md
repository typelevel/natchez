# Kernels

Each `Span` has a `Kernel`, which is a back-end-specific hunk of data that can be passed to another computer, allowing that computer to continue the current span. This is how tracing becomes **distributed**.

## Kernel Data

`Kernel` wraps a single value `toHeaders: Map[String, String]`, allowing for straightforward serialization. These values are typically turned into HTTP headers (and parsed from headers on the other end). See the `EntryPoint` @ref:[reference](entrypoints.md) for an example.

## Http4s Client Example

_The examples in this section use the following imports:_
```scala mdoc:reset
import cats.effect.IO
import natchez.{ Span }
import org.http4s.{ EntityDecoder, Uri, Header }
import org.http4s.Method.GET
import org.http4s.client.Client
import org.http4s.client.dsl.io._
```

We can add a `Span`'s kernel to an outgoing `Client` request. If the remote server supports tracing via the same back end, it can extend the trace with child spans.

```scala mdoc
def makeRequest[A](span: Span[IO], client: Client[IO], uri: Uri)(
  implicit ev: EntityDecoder[IO, A]
): IO[A] =
  span.kernel.flatMap { k =>
    // turn a Map[String, String] into List[Header]
    val http4sHeaders = k.toHeaders.map { case (k, v) => Header.Raw(k, v) } .toSeq
    client.expect[A](GET(uri).withHeaders(http4sHeaders))
  }
```

@@@note
The `natchez-http4s` project provides server and client middlewares to receive and propagate kernels, as well as lifting machinery for `Trace` constraints. Please [use it](https://github.com/tpolecat/natchez-http4s) instead of writing it yourself!
@@@
