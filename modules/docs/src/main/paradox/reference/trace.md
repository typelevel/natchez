# Trace Constraint

We must be aware of the current `Span` if we wish to add fields or create child spans, which means we must pass the current span around.

It is perfectly fine to pass the current span around explicitly, and if you are working in a concrete effect type like `IO` in Cats-Effect 2 this is likely the best choice. However for the increasingly common case of applications constructed in tagless style (where computations are peformed in some abstract effect) we have a `Trace` constraint that ensures an ambient span is always available.

_The examples on this page use the following imports:_
```scala mdoc
import cats.{ Applicative, Monad }
import cats.data.Kleisli
import cats.effect.MonadCancel
import cats.mtl.Local
import cats.syntax.all._
import natchez.{ Span, Trace }
import natchez.mtl._
```

Given an effect `F[_]: Trace` we can add fields to the ambient span, gets its kernel, or run a computation in a child of the ambient span.

```scala mdoc
def wibble[F[_]: Monad: Trace](name: String, age: Int): F[Unit] =
  Trace[F].span("wibble") {
    for {
      _ <- Trace[F].put("name" -> name, "age" -> age)
      // ... actual method logic in F
    } yield ()
  }
```

By adding a `Trace` constraint to our abstract we gain this capability.

Now that we have written a program in `F[_]: Trace` we need a way to satisfy this constraint. There are several ways to do this.

## No-Op Instance

There is a no-op `Trace[F]` instance available for all `F[_]: Applicative`. This instance does as you might expect: nothing. This can be useful for development and for testing (where trace data is typically not necessary).

```scala mdoc
object NoTraceForYou {

  // In this scope we will ignore tracing
  import natchez.Trace.Implicits.noop

  // So this compiles fine
  def go[F[_]: Monad]: F[Unit] =
    wibble("bob", 42)

}
```

## Kleisli Instance

Given `MonadCancel[F, Throwable]` there is a `Trace[F]` instance for `Kleisli[F, Span[F], *]`. This allows us to discharge a `Trace` constraint, given an initial `Span`.

```scala mdoc
// Given MonadCancel[F, Throwable] and a Span[F] we can call `wibble`
def go[F[_]](span: Span[F])(
  implicit ev: MonadCancel[F, Throwable]
): F[Unit] =
  wibble[Kleisli[F, Span[F], *]]("bob", 42).run(span)
```

This strategy works if we write in tagless style. Our outer effect is instantiated as some effect `F` and the effect for the traced part of our program is instantiated as `Kleisli[F, Span[F], *]`.

@@@warning
It is not always possible instantiate arbitrary `F` as `Kleisli`, depending on the additional constraints on `F`. In particular it would be impossbile to call `wibble[F[_]: ConcurrentEffect: Trace]` in this way.
@@@

## Cats-MTL Local Instance

By first adding the `natchez-mtl` module (which pulls in Cats-MTL), given `MonadCancel[F, Throwable]` and `Local[F, Span[F]]` there is an instance `Trace[F]`.

```scala mdoc
def goLocal[F[_]](
  implicit ev0: MonadCancel[F, Throwable],
           ev1: Local[F, Span[F]]
): F[Unit] =
  wibble("bob", 42)
```

This is more general than the `Kleisli` instance above and allows you to instantiate `F` as `RWST` for example, at the cost of an additional dependency.

## Cats-Effect 3 IO Instance

Given an `EntryPoint[IO]` you can construct a `Trace[IO]` for **Cats-Effect 3**.
This will create a root span for each requested child span.

```scala mdoc
import cats.effect.IO
import natchez.EntryPoint

def goIO(ep: EntryPoint[IO]): IO[Unit] =
  Trace.ioTraceForEntryPoint(ep).flatMap { implicit trace =>
    wibble[IO]("bob", 42)
  }
```

Alternatively, a `Trace[IO]` can be constructed from a `Span[IO]` directly.

```scala mdoc
import cats.effect.IO

def goIO(span: Span[IO]): IO[Unit] =
  Trace.ioTrace(span).flatMap { implicit trace =>
    wibble[IO]("bob", 42)
  }
```

Both methods use `IOLocal` to pass the span around. For Cats-Effect 2 you will
need to use `Kleisli` or `Local` as described above.
