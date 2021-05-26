# Log

The `natchez-log` module provides a back end that logs traces locally (as JSON) to a [log4cats](https://github.com/typelevel/log4cats) `Logger`, with no distributed functionality. To use it, add the following dependency.

@@dependency[sbt,Maven,Gradle] {
  group="$org$"
  artifact="natchez-log_2.13"
  version="$version$"
}

## Example Trace

The log handler constructs a JSON object for each root span, with a `children` collection of child spans. On completion of the root span the entire JSON blob is logged as a single message. Here is an example with a root span containing two custom fields and two child spans.

```scala mdoc:passthrough
import cats.effect._
import cats.syntax.all._
import org.typelevel.log4cats.Logger
import natchez.log.Log

// mdoc can't see output from SLF4J so let's just hack up a logger
implicit val log: Logger[IO] =
  new Logger[IO] {
    def debug(t: Throwable)(message: => String): cats.effect.IO[Unit] = ???
    def error(t: Throwable)(message: => String): cats.effect.IO[Unit] = ???
    def info(t: Throwable)(message: => String): cats.effect.IO[Unit] = ???
    def trace(t: Throwable)(message: => String): cats.effect.IO[Unit] = ???
    def warn(t: Throwable)(message: => String): cats.effect.IO[Unit] = ???
    def debug(message: => String): cats.effect.IO[Unit] = ???
    def error(message: => String): cats.effect.IO[Unit] = ???
    def info(message: => String): cats.effect.IO[Unit] = IO(println(message))
    def trace(message: => String): cats.effect.IO[Unit] = ???
    def warn(message: => String): cats.effect.IO[Unit] = ???
  }

val prog =
  Log.entryPoint[IO]("example-service").root("root span").use { s =>
    s.put("person.name" -> "bob", "person.age" -> 42) *>
    s.span("thing one").use { _ => IO.unit } *>
    s.span("thing two").use { _ => IO(ExitCode.Success) }
  }

println("```json")
prog.unsafeRunSync()
println("```")
```

## Constructing an EntryPoint

You can construct a log `EntryPoint` given an implicit `Logger[F]`.

```scala mdoc:reset:silent
import cats.effect.IO
import natchez.EntryPoint
import natchez.log.Log
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

implicit val log: Logger[IO] =
  Slf4jLogger.getLoggerFromName("example-logger")

val ep: EntryPoint[IO] =
  Log.entryPoint[IO]("example-service")
```

`Log.entryPoint` takes an optional second parameter `format: io.circe.Json => String` that can be used to manipulate the structure and content of the trace and customize the way it is formatted. The default value is `_.spaces2`.


## Log Fields

Log spans include the following fields, in addition to any user-specified fields:

| Field                   | Json Type                 | Meaning                               |
|-------------------------|---------------------------|---------------------------------------|
| `service`               | String                    | User-supplied service name.           |
| `timestamp`             | String                    | ISO-8601 date and time of span start. |
| `name`                  | String                    | User-supplied span name.              |
| `trace.trace_id`        | String (UUID)             | Id shared by all spans in a trace.    |
| `trace.span_id`         | String (UUID)             | Id of the parent span, if any.        |
| `trace.parent_id`       | String (UUID, Optional)   | Id of the parent span, if any.        |
| `duration_ms`           | Number                    | Span duration, in milliseconds.       |
| `exit.case`             | String                    | `completed`, `canceled`, or `error`   |
| `exit.error.class`      | String (Optional)         | Exception classname, if any.          |
| `exit.error.message`    | String (Optional)         | Exception message, if any.            |
| `exit.error.stackTrace` | List of String (Optional) | Exception stack trace, if any.        |

@@@note { title=Tip }
If you don't want to see the trace/span IDs or other fields, you can filter them out by supplying a `format` argument to `entryPoint` that manipulates the JSON however you like.
@@@