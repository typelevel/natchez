# No-Op

The no-op back end is provided as a `Trace` instance that does no tracing and discards all field information. It can be useful during development, as well as in testing (where trace information may not be needed).

To use the no-op back end, import the instance.

```scala mdoc
import natchez.Trace.Implicits.noop
```

Once in scope `Trace[F]` will be available implicitly for any applicative functor `F`.

```scala mdoc
import natchez.Trace

def foo[F[_]: Trace]: F[Unit] = ???

// Trace[Option] is satisfied, for example.
def go = foo[Option]

```

