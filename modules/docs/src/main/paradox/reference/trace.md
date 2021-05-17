# Trace Constraint

We must be aware of the current `Span` if we wish to add fields or create child spans, which means we must pass the current span around.

It is perfectly fine to pass the current span around explicitly, and if you are working in a concrete effect type like `IO` in Cats-Effect 2 this is likely the best choice. However for the increasingly common case of applications constructed in tagless style (where computations are peformed in some abstract effect) we have a `Trace` constraint that ensures an ambient span is always available.

Given an effect `F[_]: Trace` we can add fields to the ambient span, gets its kernel, or run a computation in a child of the ambient span.


```scala:mdoc
def wibble[F[_]: Trace](name: String, age: Int, parent: Span[F]): F[Unit] =
  Trace[F].span("wibble") {
    for {
      _ <- Trace[F].put("name" -> name, "age" -> age)
      // ... actual method logic in F
    } yield ()
  }
```

By adding a `Trace` constraint to our abstract we gain this capability.

## Discharging a Trace Constraint

Now that we have written a program in `F[_]: Trace` we need a way to satisfy this constraint. There are several ways to do this.

### No-Op Instance

There is a no-op `Trace[F]` instance available for all `F[_]: Applicative`. This instance does as you might expect: nothing. This can be useful for development and for testing (where trace data is typically not necessary).

```scala
import natchez.Trace.Implicits.noop // no trace for you!
```

### Kleisli

Given `Bracket[F, Throwable]` there is a `Trace[F]` instance for `Kleisli[F, Span[F], *]`.

TODO: exemple

### Local

By first adding the `natchez-mtl` module (which pulls in Cats-MTL), given `Bracket[F, Throwable]` and `Local[F, Span[F]]` there is an instance `Trace[F]`.

```
import nathez.mtl._
TODO
```

### IO

Given a `Span[F]` you can construct a `Trace[IO]` for **Cats-Effect 3** (for Cats-Effect 2 you will need to use `Kleisli` or `Local` above). This uses `FiberLocal` to pass the span around.

TODO: Example
