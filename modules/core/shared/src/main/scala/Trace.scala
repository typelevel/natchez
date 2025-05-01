// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats._
import cats.arrow.FunctionK
import cats.data._
import cats.effect._
import cats.syntax.all._
import fs2.Stream
import java.net.URI

/** A tracing effect, which always has a current span. */
trait Trace[F[_]] {

  /** Puts a sequence of fields into the current span. */
  def put(fields: (String, TraceValue)*): F[Unit]

  /** Logs a sequence of fields on the current span. */
  def log(fields: (String, TraceValue)*): F[Unit]

  /** Logs a single event on the current span. */
  def log(event: String): F[Unit]

  /** Adds error information to the current span. */
  def attachError(err: Throwable, fields: (String, TraceValue)*): F[Unit]

  /** The kernel for the current span, which can be sent as headers to remote systems, which can
    * then continue this trace by constructing spans that are children of the current one.
    */
  def kernel: F[Kernel]

  /** Creates a new span as a resource. */
  def spanR(name: String, options: Span.Options = Span.Options.Defaults): Resource[F, F ~> F]

  /** Create a new span, and within it run the continuation `k`. */
  def span[A](name: String, options: Span.Options = Span.Options.Defaults)(k: F[A]): F[A]

  /** Same as [[span]], expressed as a [[cats.arrow.FunctionK]]. */
  def spanK(name: String, options: Span.Options = Span.Options.Defaults): F ~> F =
    new (F ~> F) {
      def apply[A](fa: F[A]): F[A] = span(name, options)(fa)
    }

  /** A unique ID for this trace, if available. This can be useful to include in error messages for
    * example, so you can quickly find the associated trace.
    */
  def traceId: F[Option[String]]

  /** A unique ID for this span, if available. This can be useful to include in error messages for
    * example, so you can quickly find the associated trace.
    */
  def spanId(implicit F: Applicative[F]): F[Option[String]] = F.pure(None)

  /** A unique URI for this trace, if available. This can be useful to include in error messages for
    * example, so you can quickly find the associated trace.
    */
  def traceUri: F[Option[URI]]

  /** Transforms this `Trace[F]` into a `Trace[G]` using the provided `F ~> G` and `G ~> F`.
    *
    * @return A new `Trace[G]` that delegates to this `Trace[F]` using the provided transformations
    */
  def imapK[G[_]](
      fk: F ~> G
  )(gk: G ~> F)(implicit F: MonadCancel[F, ?], G: MonadCancel[G, ?]): Trace[G] = {
    val outer = this

    new Trace[G] {
      override def put(fields: (String, TraceValue)*): G[Unit] = fk(outer.put(fields*))

      override def log(fields: (String, TraceValue)*): G[Unit] = fk(outer.log(fields*))

      override def log(event: String): G[Unit] = fk(outer.log(event))

      override def attachError(err: Throwable, fields: (String, TraceValue)*): G[Unit] =
        fk(outer.attachError(err, fields*))

      override def kernel: G[Kernel] = fk(outer.kernel)

      override def spanR(name: String, options: Span.Options): Resource[G, G ~> G] =
        outer
          .spanR(name, options)
          .mapK(fk)
          .map(_.compose(gk).andThen(fk))

      override def span[A](name: String, options: Span.Options)(k: G[A]): G[A] =
        fk(outer.span(name, options)(gk(k)))

      override def spanK(name: String, options: Span.Options): G ~> G =
        outer.spanK(name, options).compose(gk).andThen(fk)

      override def traceId: G[Option[String]] =
        fk(outer.traceId)

      override def spanId(implicit FF: Applicative[G]): G[Option[String]] =
        fk(outer.spanId)

      override def traceUri: G[Option[URI]] =
        fk(outer.traceUri)
    }
  }
}

object Trace {

  def apply[F[_]](implicit ev: Trace[F]): ev.type = ev

  /** A `Trace` instance that uses `IOLocal` internally. */
  def ioTrace(rootSpan: Span[IO]): IO[Trace[IO]] =
    IOLocal(rootSpan).map { local =>
      new Trace[IO] {

        override def put(fields: (String, TraceValue)*): IO[Unit] =
          local.get.flatMap(_.put(fields: _*))

        override def attachError(err: Throwable, fields: (String, TraceValue)*): IO[Unit] =
          local.get.flatMap(_.attachError(err, fields: _*))

        override def log(fields: (String, TraceValue)*): IO[Unit] =
          local.get.flatMap(_.log(fields: _*))

        override def log(event: String): IO[Unit] =
          local.get.flatMap(_.log(event))

        override def kernel: IO[Kernel] =
          local.get.flatMap(_.kernel)

        override def spanR(name: String, options: Span.Options): Resource[IO, IO ~> IO] =
          for {
            parent <- Resource.eval(local.get)
            child <- parent.span(name, options)
          } yield new (IO ~> IO) {
            def apply[A](fa: IO[A]): IO[A] =
              local.get.flatMap { old =>
                local
                  .set(child)
                  .bracket(_ => fa.onError { case e => child.attachError(e) })(_ => local.set(old))
              }

          }

        override def span[A](name: String, options: Span.Options)(k: IO[A]): IO[A] =
          spanR(name, options).use(_(k))

        override def traceId: IO[Option[String]] =
          local.get.flatMap(_.traceId)

        override def spanId(implicit F: Applicative[IO]): IO[Option[String]] =
          local.get.flatMap(_.spanId)

        override def traceUri: IO[Option[URI]] =
          local.get.flatMap(_.traceUri)
      }
    }

  /** A `Trace` instance that uses `IOLocal` internally. Span creation delegates to the supplied entry point. */
  def ioTraceForEntryPoint(ep: EntryPoint[IO]): IO[Trace[IO]] =
    ioTrace(Span.makeRoots(ep))

  object Implicits {

    /** A no-op `Trace` implementation is freely available for any applicative effect. This lets us add
      * a `Trace` constraint to most existing code without demanding anything new from the concrete
      * effect type.
      */
    implicit def noop[F[_]: Applicative]: Trace[F] =
      new Trace[F] {
        final val void = Applicative[F].unit
        override val kernel: F[Kernel] = Kernel(Map.empty).pure[F]
        override def put(fields: (String, TraceValue)*): F[Unit] = void
        override def attachError(err: Throwable, fields: (String, TraceValue)*): F[Unit] = void
        override def log(fields: (String, TraceValue)*): F[Unit] = void
        override def log(event: String): F[Unit] = void
        override def spanR(name: String, options: Span.Options): Resource[F, F ~> F] =
          Resource.pure(FunctionK.id)
        override def span[A](name: String, options: Span.Options)(k: F[A]): F[A] = k
        override def traceId: F[Option[String]] = none.pure[F]
        override def spanId(implicit A: Applicative[F]): F[Option[String]] =
          A.pure(None)
        override def traceUri: F[Option[URI]] = none.pure[F]
      }
  }

  /** `Kleisli[F, Span[F], *]` is a `Trace` given `MonadCancel[F, Throwable]`. The instance can be
    * widened to an environment that *contains* a `Span[F]` via the `lens` method.
    */
  implicit def kleisliInstance[F[_]](implicit ev: MonadCancel[F, Throwable]): KleisliTrace[F] =
    new KleisliTrace[F]

  /** A trace instance for `Kleisli[F, Span[F], *]`, which is the mechanism we use to introduce
    * context into our computations. We can also "lensMap" out to `Kleisli[F, E, *]` given a lens
    * from `E` to `Span[F]`.
    */
  class KleisliTrace[F[_]](implicit ev: MonadCancel[F, Throwable])
      extends Trace[Kleisli[F, Span[F], *]] {

    override def kernel: Kleisli[F, Span[F], Kernel] =
      Kleisli(_.kernel)

    override def put(fields: (String, TraceValue)*): Kleisli[F, Span[F], Unit] =
      Kleisli(_.put(fields: _*))

    override def attachError(
        err: Throwable,
        fields: (String, TraceValue)*
    ): Kleisli[F, Span[F], Unit] =
      Kleisli(_.attachError(err, fields: _*))

    override def log(fields: (String, TraceValue)*): Kleisli[F, Span[F], Unit] =
      Kleisli(_.log(fields: _*))

    override def log(event: String): Kleisli[F, Span[F], Unit] =
      Kleisli(_.log(event))

    override def spanR(
        name: String,
        options: Span.Options
    ): Resource[Kleisli[F, Span[F], *], Kleisli[F, Span[F], *] ~> Kleisli[F, Span[F], *]] =
      Resource.applyFull { cancelable =>
        cancelable(Kleisli((span: Span[F]) => span.span(name, options).allocatedCase)).map {
          case (child, release) =>
            new (Kleisli[F, Span[F], *] ~> Kleisli[F, Span[F], *]) {
              def apply[A](fa: Kleisli[F, Span[F], A]): Kleisli[F, Span[F], A] =
                fa.local((_: Span[F]) => child).mapF(_.onError { case e => child.attachError(e) })
            } -> release.andThen(Kleisli.liftF[F, Span[F], Unit](_))
        }
      }

    override def span[A](name: String, options: Span.Options)(
        k: Kleisli[F, Span[F], A]
    ): Kleisli[F, Span[F], A] =
      spanR(name, options).use(_(k))

    def lens[E](f: E => Span[F], g: (E, Span[F]) => E): Trace[Kleisli[F, E, *]] =
      new Trace[Kleisli[F, E, *]] {

        override def kernel: Kleisli[F, E, Kernel] =
          Kleisli(e => f(e).kernel)

        override def put(fields: (String, TraceValue)*): Kleisli[F, E, Unit] =
          Kleisli(e => f(e).put(fields: _*))

        override def attachError(
            err: Throwable,
            fields: (String, TraceValue)*
        ): Kleisli[F, E, Unit] =
          Kleisli(e => f(e).attachError(err, fields: _*))

        override def log(fields: (String, TraceValue)*): Kleisli[F, E, Unit] =
          Kleisli(e => f(e).log(fields: _*))

        override def log(event: String): Kleisli[F, E, Unit] =
          Kleisli(e => f(e).log(event))

        override def spanR(
            name: String,
            options: Span.Options
        ): Resource[Kleisli[F, E, *], Kleisli[F, E, *] ~> Kleisli[F, E, *]] =
          Resource.applyFull { cancelable =>
            Kleisli
              .ask[F, E]
              .flatMap(e =>
                cancelable(Kleisli.liftF(f(e).span(name, options).allocatedCase)).map {
                  case (child, release) =>
                    new (Kleisli[F, E, *] ~> Kleisli[F, E, *]) {
                      def apply[A](fa: Kleisli[F, E, A]): Kleisli[F, E, A] =
                        fa.local((_: E) => g(e, child))
                          .mapF(_.onError { case err => child.attachError(err) })
                    } -> release.andThen(Kleisli.liftF[F, E, Unit](_))
                }
              )
          }

        override def span[A](name: String, options: Span.Options)(
            k: Kleisli[F, E, A]
        ): Kleisli[F, E, A] =
          spanR(name, options).use(_(k))

        override def traceId: Kleisli[F, E, Option[String]] =
          Kleisli(e => f(e).traceId)

        override def spanId(implicit
            F: Applicative[Kleisli[F, E, *]]
        ): Kleisli[F, E, Option[String]] =
          Kleisli(e => f(e).spanId)

        override def traceUri: Kleisli[F, E, Option[URI]] =
          Kleisli(e => f(e).traceUri)
      }

    override def traceId: Kleisli[F, Span[F], Option[String]] =
      Kleisli(_.traceId)

    override def spanId(implicit
        F: Applicative[Kleisli[F, Span[F], *]]
    ): Kleisli[F, Span[F], Option[String]] =
      Kleisli(_.spanId)

    override def traceUri: Kleisli[F, Span[F], Option[URI]] =
      Kleisli(_.traceUri)
  }

  implicit def liftKleisli[F[_]: MonadCancelThrow, E](implicit
      trace: Trace[F]
  ): Trace[Kleisli[F, E, *]] =
    new Trace[Kleisli[F, E, *]] {

      override def put(fields: (String, TraceValue)*): Kleisli[F, E, Unit] =
        Kleisli.liftF(trace.put(fields: _*))

      override def attachError(err: Throwable, fields: (String, TraceValue)*): Kleisli[F, E, Unit] =
        Kleisli.liftF(trace.attachError(err, fields: _*))

      override def log(fields: (String, TraceValue)*): Kleisli[F, E, Unit] =
        Kleisli.liftF(trace.log(fields: _*))

      override def log(event: String): Kleisli[F, E, Unit] =
        Kleisli.liftF(trace.log(event))

      override def kernel: Kleisli[F, E, Kernel] =
        Kleisli.liftF(trace.kernel)

      override def spanR(
          name: String,
          options: Span.Options
      ): Resource[Kleisli[F, E, *], Kleisli[F, E, *] ~> Kleisli[F, E, *]] =
        Resource.applyFull { cancelable =>
          Kleisli
            .ask[F, E]
            .flatMap { e =>
              cancelable(Kleisli.liftF(trace.spanR(name, options).allocatedCase)).map {
                case (f, release) =>
                  f.compose(Kleisli.applyK(e)).andThen(Kleisli.liftK[F, E]) ->
                    release.andThen(f(_)).andThen(Kleisli.liftF[F, E, Unit])
              }
            }
        }

      override def span[A](name: String, options: Span.Options)(
          k: ReaderT[F, E, A]
      ): ReaderT[F, E, A] =
        Kleisli(e => trace.span[A](name, options)(k.run(e)))

      override def traceId: Kleisli[F, E, Option[String]] =
        Kleisli.liftF(trace.traceId)

      override def spanId(implicit
          F: Applicative[Kleisli[F, E, *]]
      ): Kleisli[F, E, Option[String]] =
        Kleisli.liftF(trace.spanId)

      override def traceUri: Kleisli[F, E, Option[URI]] =
        Kleisli.liftF(trace.traceUri)
    }

  implicit def liftStateT[F[_]: MonadCancelThrow, S](implicit
      trace: Trace[F]
  ): Trace[StateT[F, S, *]] =
    new Trace[StateT[F, S, *]] {
      override def put(fields: (String, TraceValue)*): StateT[F, S, Unit] =
        StateT.liftF(trace.put(fields: _*))

      override def attachError(err: Throwable, fields: (String, TraceValue)*): StateT[F, S, Unit] =
        StateT.liftF(trace.attachError(err, fields: _*))

      override def log(fields: (String, TraceValue)*): StateT[F, S, Unit] =
        StateT.liftF(trace.log(fields: _*))

      override def log(event: String): StateT[F, S, Unit] =
        StateT.liftF(trace.log(event))

      override def kernel: StateT[F, S, Kernel] =
        StateT.liftF(trace.kernel)

      override def spanR(
          name: String,
          options: Span.Options
      ): Resource[StateT[F, S, *], StateT[F, S, *] ~> StateT[F, S, *]] =
        Resource.applyFull { cancelable =>
          cancelable(StateT.liftF(trace.spanR(name, options).allocatedCase)).map {
            case (f, release) =>
              new (StateT[F, S, *] ~> StateT[F, S, *]) {
                def apply[A](fa: StateT[F, S, A]): StateT[F, S, A] =
                  StateT.applyF(f(fa.runF))
              } ->
                release.andThen(f(_)).andThen(StateT.liftF[F, S, Unit](_))
          }
        }

      override def span[A](name: String, options: Span.Options)(
          k: StateT[F, S, A]
      ): StateT[F, S, A] =
        StateT(s => trace.span[(S, A)](name, options)(k.run(s)))

      override def traceId: StateT[F, S, Option[String]] =
        StateT.liftF(trace.traceId)

      override def spanId(implicit
          F: Applicative[StateT[F, S, *]]
      ): StateT[F, S, Option[String]] =
        StateT.liftF(trace.spanId)

      override def traceUri: StateT[F, S, Option[URI]] =
        StateT.liftF(trace.traceUri)
    }

  implicit def liftEitherT[F[_]: MonadCancelThrow, E](implicit
      trace: Trace[F]
  ): Trace[EitherT[F, E, *]] =
    new Trace[EitherT[F, E, *]] {

      override def put(fields: (String, TraceValue)*): EitherT[F, E, Unit] =
        EitherT.liftF(trace.put(fields: _*))

      override def attachError(err: Throwable, fields: (String, TraceValue)*): EitherT[F, E, Unit] =
        EitherT.liftF(trace.attachError(err, fields: _*))

      override def log(fields: (String, TraceValue)*): EitherT[F, E, Unit] =
        EitherT.liftF(trace.log(fields: _*))

      override def log(event: String): EitherT[F, E, Unit] =
        EitherT.liftF(trace.log(event))

      override def kernel: EitherT[F, E, Kernel] =
        EitherT.liftF(trace.kernel)

      override def spanR(
          name: String,
          options: Span.Options
      ): Resource[EitherT[F, E, *], EitherT[F, E, *] ~> EitherT[F, E, *]] =
        Resource.applyFull { cancelable =>
          cancelable(EitherT.liftF(trace.spanR(name, options).allocatedCase)).map {
            case (f, release) =>
              new (EitherT[F, E, *] ~> EitherT[F, E, *]) {
                def apply[A](fa: EitherT[F, E, A]): EitherT[F, E, A] =
                  EitherT(f(fa.value))
              } ->
                release.andThen(f(_)).andThen(EitherT.liftF[F, E, Unit])
          }
        }

      override def span[A](name: String, options: Span.Options)(
          k: EitherT[F, E, A]
      ): EitherT[F, E, A] =
        EitherT(trace.span(name, options)(k.value))

      override def traceId: EitherT[F, E, Option[String]] =
        EitherT.liftF(trace.traceId)

      override def spanId(implicit
          F: Applicative[EitherT[F, E, *]]
      ): EitherT[F, E, Option[String]] =
        EitherT.liftF(trace.spanId)

      override def traceUri: EitherT[F, E, Option[URI]] =
        EitherT.liftF(trace.traceUri)
    }

  implicit def liftOptionT[F[_]: MonadCancelThrow](implicit trace: Trace[F]): Trace[OptionT[F, *]] =
    new Trace[OptionT[F, *]] {

      override def put(fields: (String, TraceValue)*): OptionT[F, Unit] =
        OptionT.liftF(trace.put(fields: _*))

      override def attachError(err: Throwable, fields: (String, TraceValue)*): OptionT[F, Unit] =
        OptionT.liftF(trace.attachError(err, fields: _*))

      override def log(fields: (String, TraceValue)*): OptionT[F, Unit] =
        OptionT.liftF(trace.log(fields: _*))

      override def log(event: String): OptionT[F, Unit] =
        OptionT.liftF(trace.log(event))

      override def kernel: OptionT[F, Kernel] =
        OptionT.liftF(trace.kernel)

      override def spanR(
          name: String,
          options: Span.Options
      ): Resource[OptionT[F, *], OptionT[F, *] ~> OptionT[F, *]] =
        Resource.applyFull { cancelable =>
          cancelable(OptionT.liftF(trace.spanR(name, options).allocatedCase)).map {
            case (f, release) =>
              new (OptionT[F, *] ~> OptionT[F, *]) {
                def apply[A](fa: OptionT[F, A]): OptionT[F, A] = fa.mapK(f)
              } ->
                release.map(f(_)).map(OptionT.liftF(_))
          }
        }

      override def span[A](name: String, options: Span.Options)(k: OptionT[F, A]): OptionT[F, A] =
        OptionT(trace.span(name, options)(k.value))

      override def traceId: OptionT[F, Option[String]] =
        OptionT.liftF(trace.traceId)

      override def spanId(implicit
          F: Applicative[OptionT[F, *]]
      ): OptionT[F, Option[String]] =
        OptionT.liftF(trace.spanId)

      override def traceUri: OptionT[F, Option[URI]] =
        OptionT.liftF(trace.traceUri)
    }

  implicit def liftNested[F[_]: MonadCancelThrow, G[_]: Applicative](implicit
      trace: Trace[F],
      FG: MonadCancelThrow[Nested[F, G, *]]
  ): Trace[Nested[F, G, *]] =
    new Trace[Nested[F, G, *]] {

      override def put(fields: (String, TraceValue)*): Nested[F, G, Unit] =
        trace.put(fields: _*).map(_.pure[G]).nested

      override def attachError(err: Throwable, fields: (String, TraceValue)*): Nested[F, G, Unit] =
        trace.attachError(err, fields: _*).map(_.pure[G]).nested

      override def log(fields: (String, TraceValue)*): Nested[F, G, Unit] =
        trace.log(fields: _*).map(_.pure[G]).nested

      override def log(event: String): Nested[F, G, Unit] =
        trace.log(event).map(_.pure[G]).nested

      override def kernel: Nested[F, G, Kernel] =
        trace.kernel.map(_.pure[G]).nested

      override def spanR(
          name: String,
          options: Span.Options
      ): Resource[Nested[F, G, *], Nested[F, G, *] ~> Nested[F, G, *]] =
        Resource.applyFull { cancelable =>
          cancelable(Nested(trace.spanR(name, options).allocatedCase.map(_.pure[G]))).map {
            case (f, release) =>
              new (Nested[F, G, *] ~> Nested[F, G, *]) {
                def apply[A](fa: Nested[F, G, A]): Nested[F, G, A] =
                  Nested(f(fa.value))
              } ->
                release.andThen(_.map(_.pure[G])).andThen(Nested(_))
          }
        }

      override def span[A](name: String, options: Span.Options)(
          k: Nested[F, G, A]
      ): Nested[F, G, A] =
        trace.span(name, options)(k.value).nested

      override def traceId: Nested[F, G, Option[String]] =
        trace.traceId.map(_.pure[G]).nested

      override def spanId(implicit
          F: Applicative[Nested[F, G, *]]
      ): Nested[F, G, Option[String]] =
        trace.spanId.map(_.pure[G]).nested

      override def traceUri: Nested[F, G, Option[URI]] =
        trace.traceUri.map(_.pure[G]).nested
    }

  implicit def liftResource[F[_]: MonadCancelThrow](implicit
      trace: Trace[F]
  ): Trace[Resource[F, *]] =
    new Trace[Resource[F, *]] {
      override def put(fields: (String, TraceValue)*): Resource[F, Unit] =
        Resource.eval(trace.put(fields: _*))

      override def kernel: Resource[F, Kernel] =
        Resource.eval(trace.kernel)

      override def attachError(err: Throwable, fields: (String, TraceValue)*): Resource[F, Unit] =
        Resource.eval(trace.attachError(err, fields: _*))

      override def log(event: String): Resource[F, Unit] =
        Resource.eval(trace.log(event))

      override def log(fields: (String, TraceValue)*): Resource[F, Unit] =
        Resource.eval(trace.log(fields: _*))

      override def spanR(
          name: String,
          options: Span.Options
      ): Resource[Resource[F, *], Resource[F, *] ~> Resource[F, *]] =
        Resource.applyFull { cancelable =>
          cancelable(Resource.eval(trace.spanR(name, options).allocatedCase)).map {
            case (f, release) =>
              new (Resource[F, *] ~> Resource[F, *]) {
                def apply[A](fa: Resource[F, A]): Resource[F, A] =
                  fa.mapK(f)
              } ->
                release.andThen(f(_)).andThen(Resource.eval(_))
          }
        }

      override def span[A](name: String, options: Span.Options)(k: Resource[F, A]): Resource[F, A] =
        trace.spanR(name, options).flatMap { f =>
          Resource.applyFull { cancelable =>
            f(cancelable(k.allocatedCase)).map { case (a, release) =>
              a -> release.andThen(f(_))
            }
          }
        }

      override def traceId: Resource[F, Option[String]] =
        Resource.eval(trace.traceId)

      override def spanId(implicit
          F: Applicative[Resource[F, *]]
      ): Resource[F, Option[String]] =
        Resource.eval(trace.spanId)

      override def traceUri: Resource[F, Option[URI]] =
        Resource.eval(trace.traceUri)
    }

  implicit def liftStream[F[_]: MonadCancelThrow](implicit trace: Trace[F]): Trace[Stream[F, *]] =
    new Trace[Stream[F, *]] {
      override def put(fields: (String, TraceValue)*): Stream[F, Unit] =
        Stream.eval(trace.put(fields: _*))

      override def kernel: Stream[F, Kernel] =
        Stream.eval(trace.kernel)

      override def attachError(err: Throwable, fields: (String, TraceValue)*): Stream[F, Unit] =
        Stream.eval(trace.attachError(err, fields: _*))

      override def log(event: String): Stream[F, Unit] =
        Stream.eval(trace.log(event))

      override def log(fields: (String, TraceValue)*): Stream[F, Unit] =
        Stream.eval(trace.log(fields: _*))

      override def spanR(
          name: String,
          options: Span.Options
      ): Resource[Stream[F, *], Stream[F, *] ~> Stream[F, *]] =
        Resource.applyFull { cancelable =>
          cancelable(Stream.eval(trace.spanR(name, options).allocatedCase)).map {
            case (f, release) =>
              new (Stream[F, *] ~> Stream[F, *]) {
                def apply[A](fa: Stream[F, A]): Stream[F, A] =
                  fa.translate(f)
              } ->
                release.andThen(f(_)).andThen(Stream.eval(_))
          }
        }

      override def span[A](name: String, options: Span.Options)(k: Stream[F, A]): Stream[F, A] =
        Stream.resource(trace.spanR(name, options)).flatMap(k.translate)

      override def traceId: Stream[F, Option[String]] =
        Stream.eval(trace.traceId)

      override def spanId(implicit
          F: Applicative[Stream[F, *]]
      ): Stream[F, Option[String]] =
        Stream.eval(trace.spanId)

      override def traceUri: Stream[F, Option[URI]] =
        Stream.eval(trace.traceUri)
    }
}
