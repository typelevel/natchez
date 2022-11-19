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

  /** Put a sequence of fields into the current span. */
  def put(fields: (String, TraceValue)*): F[Unit]

  /**
   * The kernel for the current span, which can be sent as headers to remote systems, which can
   * then continue this trace by constructing spans that are children of the current one.
   */
  def kernel: F[Kernel]

  /** Creates a new span, and within it acquires and releases the spanR `r`. */
  def spanR(name: String): Resource[F, F ~> F]

  def spanR(name: String, kernel: Kernel): Resource[F, F ~> F]

  /** Create a new span, and within it run the continuation `k`. */
  def span[A](name: String)(k: F[A]): F[A]

  /** Create a new span and add current span and kernel to parents of new span */
  def span[A](name: String, kernel: Kernel)(k: F[A]): F[A]

  /**
   * A unique ID for this trace, if available. This can be useful to include in error messages for
   * example, so you can quickly find the associated trace.
   */
  def traceId: F[Option[String]]

  /**
   * A unique URI for this trace, if available. This can be useful to include in error messages for
   * example, so you can quickly find the associated trace.
   */
  def traceUri: F[Option[URI]]

}

object Trace {

  def apply[F[_]](implicit ev: Trace[F]): ev.type = ev

  /** A `Trace` instance that uses `IOLocal` internally. */
  def ioTrace(rootSpan: Span[IO]): IO[Trace[IO]] =
    IOLocal(rootSpan).map { local =>
      new Trace[IO] {

        def put(fields: (String, TraceValue)*): IO[Unit] =
          local.get.flatMap(_.put(fields: _*))

        def kernel: IO[Kernel] =
          local.get.flatMap(_.kernel)

        def spanR(name: String): Resource[IO, IO ~> IO] =
          for {
            parent <- Resource.eval(local.get)
            child <- parent.span(name)
          } yield new (IO ~> IO) {
            def apply[A](fa: IO[A]): IO[A] =
              local.set(child).bracket(_ => fa)(_ => local.set(parent))
          }

        def spanR(name: String, kernel: Kernel): Resource[IO, IO ~> IO] =
          for {
            parent <- Resource.eval(local.get)
            child <- parent.span(name, kernel)
          } yield new (IO ~> IO) {
            def apply[A](fa: IO[A]): IO[A] =
              local.set(child).bracket(_ => fa)(_ => local.set(parent))
          }

        def span[A](name: String)(k: IO[A]): IO[A] =
          spanR(name).surround(k)

        def traceId: IO[Option[String]] =
          local.get.flatMap(_.traceId)

        def traceUri: IO[Option[URI]] =
          local.get.flatMap(_.traceUri)

        override def span[A](name: String, kernel: Kernel)(k: IO[A]): IO[A] =
          local.get.flatMap { parent =>
            parent.span(name, kernel).flatMap { child =>
              Resource.make(local.set(child))(_ => local.set(parent))
            }.use { _ => k }
          }
      }
    }

  object Implicits {

    /**
     * A no-op `Trace` implementation is freely available for any applicative effect. This lets us add
     * a `Trace` constraint to most existing code without demanding anything new from the concrete
     * effect type.
     */
    implicit def noop[F[_]: Applicative]: Trace[F] =
      new Trace[F] {
        final val void = ().pure[F]
        val kernel: F[Kernel] = Kernel(Map.empty).pure[F]
        def put(fields: (String, TraceValue)*): F[Unit] = void
        def spanR(name: String): Resource[F, F ~> F] =
          Resource.pure(FunctionK.id)
        def span[A](name: String)(k: F[A]): F[A] = k
        def traceId: F[Option[String]] = none.pure[F]
        def traceUri: F[Option[URI]] = none.pure[F]
      }

  }

  /**
   * `Kleisli[F, Span[F], *]` is a `Trace` given `MonadCancel[F, Throwable]`. The instance can be
   * widened to an environment that *contains* a `Span[F]` via the `lens` method.
   */
  implicit def kleisliInstance[F[_]](implicit ev: MonadCancel[F, Throwable]): KleisliTrace[F] =
    new KleisliTrace[F]

  /**
   * A trace instance for `Kleisli[F, Span[F], *]`, which is the mechanism we use to introduce
   * context into our computations. We can also "lensMap" out to `Kleisli[F, E, *]` given a lens
   * from `E` to `Span[F]`.
   */
  class KleisliTrace[F[_]](implicit ev: MonadCancel[F, Throwable]) extends Trace[Kleisli[F, Span[F], *]] {

    def kernel: Kleisli[F, Span[F], Kernel] =
      Kleisli(_.kernel)

    def put(fields: (String, TraceValue)*): Kleisli[F, Span[F], Unit] =
      Kleisli(_.put(fields: _*))

    def spanR(name: String): Resource[Kleisli[F, Span[F], *], Kleisli[F, Span[F], *] ~> Kleisli[F, Span[F], *]] =
      Resource(Kleisli((span: Span[F]) => span.span(name).allocated.map {
        case (child, release) =>
          new (Kleisli[F, Span[F], *] ~> Kleisli[F, Span[F], *]) {
            def apply[A](fa: Kleisli[F, Span[F], A]): Kleisli[F, Span[F], A] =
              fa.local(_ => child)
          } -> Kleisli.liftF[F, Span[F], Unit](release)
      }))

    def span[A](name: String)(k: Kleisli[F, Span[F], A]): Kleisli[F,Span[F],A] =
      Kleisli(_.span(name).use(k.run))

    def lens[E](f: E => Span[F], g: (E, Span[F]) => E): Trace[Kleisli[F, E, *]] =
      new Trace[Kleisli[F, E, *]] {

        def kernel: Kleisli[F,E,Kernel] =
          Kleisli(e => f(e).kernel)

        def put(fields: (String, TraceValue)*): Kleisli[F,E,Unit] =
          Kleisli(e => f(e).put(fields: _*))

        def spanR(name: String): Resource[Kleisli[F, E, *], Kleisli[F, E, *] ~> Kleisli[F, E, *]] =
          Resource(Kleisli((e: E) => f(e).span(name).allocated.map {
            case (child, release) =>
              new (Kleisli[F, E, *] ~> Kleisli[F, E, *]) {
                def apply[A](fa: Kleisli[F, E, A]): Kleisli[F, E, A] =
                  fa.local(_ => g(e, child))
              } -> Kleisli.liftF[F, E, Unit](release)
          }))

        def span[A](name: String)(k: Kleisli[F, E, A]): Kleisli[F, E, A] =
          Kleisli(e => f(e).span(name).use(s => k.run(g(e, s))))

        def traceId: Kleisli[F,E,Option[String]] =
          Kleisli(e => f(e).traceId)

        def traceUri: Kleisli[F,E,Option[URI]] =
          Kleisli(e => f(e).traceUri)

        def span[A](name: String, kernel: Kernel)(k: Kleisli[F, E, A]): Kleisli[F, E, A] =
          Kleisli(e => f(e).span(name, kernel).use(s => k.run(g(e, s))))

        def spanR(name: String, kernel: Kernel): Resource[ReaderT[F, E, *], Kleisli[F, E, *] ~> Kleisli[F, E, *]] =
          Resource(Kleisli((e: E) => f(e).span(name, kernel).allocated.map {
            case (child, release) =>
              new (Kleisli[F, E, *] ~> Kleisli[F, E, *]) {
                def apply[A](fa: Kleisli[F, E, A]): Kleisli[F, E, A] =
                  fa.local(_ => g(e, child))
              } -> Kleisli.liftF[F, E, Unit](release)
          }))
      }

    def traceId: Kleisli[F,Span[F],Option[String]] =
      Kleisli(_.traceId)

    def traceUri: Kleisli[F,Span[F],Option[URI]] =
      Kleisli(_.traceUri)

    def span[A](name: String, kernel: Kernel)(k: ReaderT[F, Span[F], A]): ReaderT[F, Span[F], A] =
      Kleisli(_.span(name, kernel))

    override def spanR(name: String, kernel: Kernel): Resource[ReaderT[F, Span[F], *], Kleisli[F, Span[F], *] ~> Kleisli[F, Span[F], *]] =
      Resource(Kleisli((span: Span[F]) => span.span(name, kernel).allocated.map {
        case (child, release) =>
          new (Kleisli[F, Span[F], *] ~> Kleisli[F, Span[F], *]) {
            def apply[A](fa: Kleisli[F, Span[F], A]): Kleisli[F, Span[F], A] =
              fa.local(_ => child)
          } -> Kleisli.liftF[F, Span[F], Unit](release)
      }))
  }

  implicit def liftKleisli[F[_]: MonadCancelThrow, E](implicit trace: Trace[F]): Trace[Kleisli[F, E, *]] =
    new Trace[Kleisli[F, E, *]] {

      def put(fields: (String, TraceValue)*): Kleisli[F, E, Unit] =
        Kleisli.liftF(trace.put(fields: _*))

      def kernel: Kleisli[F, E, Kernel] =
        Kleisli.liftF(trace.kernel)

      def spanR(name: String): Resource[Kleisli[F, E, *], Kleisli[F, E, *] ~> Kleisli[F, E, *]] =
        Resource(
          Kleisli((e: E) =>
            trace.spanR(name).allocated.map { case (f, release) =>
              f.compose(Kleisli.applyK(e)).andThen(Kleisli.liftK[F, E]) ->
              Kleisli.liftF[F, E, Unit](f(release))
            }
          )
        )

      def span[A](name: String)(k: Kleisli[F, E, A]): Kleisli[F, E, A] =
        Kleisli(e => trace.span[A](name)(k.run(e)))

      def traceId: Kleisli[F, E, Option[String]] =
        Kleisli.liftF(trace.traceId)

      def traceUri: Kleisli[F, E, Option[URI]] =
        Kleisli.liftF(trace.traceUri)

      def span[A](name: String, kernel: Kernel)(k: ReaderT[F, E, A]): ReaderT[F, E, A] =
        Kleisli(e => trace.span[A](name, kernel)(k.run(e)))

      override def spanR(name: String, kernel: Kernel): Resource[ReaderT[F, E, *], Kleisli[F, E, *] ~> Kleisli[F, E, *]] =
        Resource(
          Kleisli((e: E) =>
            trace.spanR(name, kernel).allocated.map { case (f, release) =>
              f.compose(Kleisli.applyK(e)).andThen(Kleisli.liftK[F, E]) ->
                Kleisli.liftF[F, E, Unit](f(release))
            }
          )
        )
    }

  implicit def liftStateT[F[_]: MonadCancelThrow, S](implicit trace: Trace[F]): Trace[StateT[F, S, *]] =
    new Trace[StateT[F, S, *]] {
      def put(fields: (String, TraceValue)*): StateT[F, S, Unit] =
        StateT.liftF(trace.put(fields: _*))

      def kernel: StateT[F, S, Kernel] =
        StateT.liftF(trace.kernel)

      def spanR(name: String): Resource[StateT[F, S, *], StateT[F, S, *] ~> StateT[F, S, *]] =
        Resource(
          StateT.liftF(
            trace.spanR(name).allocated.map { case (f, release) =>
              new (StateT[F, S, *] ~> StateT[F, S, *]) {
                def apply[A](fa: StateT[F, S, A]): StateT[F, S, A] =
                  StateT.applyF(f(fa.runF))
              } ->
              StateT.liftF[F, S, Unit](f(release))
            }
          )
        )

      def span[A](name: String)(k: StateT[F, S, A]): StateT[F, S, A] =
        StateT(s => trace.span[(S, A)](name)(k.run(s)))

      def traceId: StateT[F, S, Option[String]] =
        StateT.liftF(trace.traceId)

      def traceUri: StateT[F, S, Option[URI]] =
        StateT.liftF(trace.traceUri)

      override def span[A](name: String, kernel: Kernel)(k: StateT[F, S, A]): StateT[F, S, A] =
        StateT(s => trace.span[(S, A)](name, kernel)(k.run(s)))

      def spanR(name: String, kernel: Kernel): Resource[StateT[F, S, *], StateT[F, S, *] ~> StateT[F, S, *]] =
        Resource(
          StateT.liftF(
            trace.spanR(name, kernel).allocated.map { case (f, release) =>
              new (StateT[F, S, *] ~> StateT[F, S, *]) {
                def apply[A](fa: StateT[F, S, A]): StateT[F, S, A] =
                  StateT.applyF(f(fa.runF))
              } ->
                StateT.liftF[F, S, Unit](f(release))
            }
          )
        )
    }

  implicit def liftEitherT[F[_]: MonadCancelThrow, E](implicit trace: Trace[F]): Trace[EitherT[F, E, *]] =
    new Trace[EitherT[F, E, *]] {

      def put(fields: (String, TraceValue)*): EitherT[F, E, Unit] =
        EitherT.liftF(trace.put(fields: _*))

      def kernel: EitherT[F, E, Kernel] =
        EitherT.liftF(trace.kernel)

      def spanR(name: String): Resource[EitherT[F, E, *], EitherT[F, E, *] ~> EitherT[F, E, *]] =
        Resource(
          EitherT.liftF(
            trace.spanR(name).allocated.map { case (f, release) =>
              new (EitherT[F, E, *] ~> EitherT[F, E, *]) {
                def apply[A](fa: EitherT[F, E, A]): EitherT[F, E, A] =
                  EitherT(f(fa.value))
              } ->
              EitherT.liftF[F, E, Unit](f(release))
            }
          )
        )

      def span[A](name: String)(k: EitherT[F, E, A]): EitherT[F, E, A] =
        EitherT(trace.span(name)(k.value))

      def traceId: EitherT[F, E, Option[String]] =
        EitherT.liftF(trace.traceId)

      def traceUri: EitherT[F, E, Option[URI]] =
        EitherT.liftF(trace.traceUri)

      def span[A](name: String, kernel: Kernel)(k: EitherT[F, E, A]): EitherT[F, E, A] =
        EitherT(trace.span(name, kernel)(k.value))

      def spanR(name: String, kernel: Kernel): Resource[EitherT[F, E, *], EitherT[F, E, *] ~> EitherT[F, E, *]] =
        Resource(
          EitherT.liftF(
            trace.spanR(name, kernel).allocated.map { case (f, release) =>
              new (EitherT[F, E, *] ~> EitherT[F, E, *]) {
                def apply[A](fa: EitherT[F, E, A]): EitherT[F, E, A] =
                  EitherT(f(fa.value))
              } ->
                EitherT.liftF[F, E, Unit](f(release))
            }
          )
        )
    }

  implicit def liftOptionT[F[_]: MonadCancelThrow](implicit trace: Trace[F]): Trace[OptionT[F, *]] =
    new Trace[OptionT[F, *]] {

      def put(fields: (String, TraceValue)*): OptionT[F, Unit] =
        OptionT.liftF(trace.put(fields: _*))

      def kernel: OptionT[F, Kernel] =
        OptionT.liftF(trace.kernel)

      def spanR(name: String): Resource[OptionT[F, *], OptionT[F, *] ~> OptionT[F, *]] =
        Resource(
          OptionT.liftF(
            trace.spanR(name).allocated.map { case (f, release) =>
              new (OptionT[F, *] ~> OptionT[F, *]) {
                def apply[A](fa: OptionT[F, A]): OptionT[F, A] =
                  OptionT(f(fa.value))
              } ->
              OptionT.liftF[F, Unit](f(release))
            }
          )
        )

      def span[A](name: String)(k: OptionT[F, A]): OptionT[F, A] =
        OptionT(trace.span(name)(k.value))

      def traceId: OptionT[F, Option[String]] =
        OptionT.liftF(trace.traceId)

      def traceUri: OptionT[F, Option[URI]] =
        OptionT.liftF(trace.traceUri)

      override def span[A](name: String, kernel: Kernel)(k: OptionT[F, A]): OptionT[F, A] =
        OptionT(trace.span(name, kernel)(k.value))

      def spanR(name: String, kernel: Kernel): Resource[OptionT[F, *], OptionT[F, *] ~> OptionT[F, *]] =
        Resource(
          OptionT.liftF(
            trace.spanR(name, kernel).allocated.map { case (f, release) =>
              new (OptionT[F, *] ~> OptionT[F, *]) {
                def apply[A](fa: OptionT[F, A]): OptionT[F, A] =
                  OptionT(f(fa.value))
              } ->
                OptionT.liftF[F, Unit](f(release))
            }
          )
        )
    }

  implicit def liftNested[F[_]: MonadCancelThrow, G[_]: Applicative](implicit trace: Trace[F], FG: MonadCancelThrow[Nested[F, G, *]]): Trace[Nested[F, G, *]] =
    new Trace[Nested[F, G, *]] {

      def put(fields: (String, TraceValue)*): Nested[F, G, Unit] =
        trace.put(fields: _*).map(_.pure[G]).nested

      def kernel: Nested[F, G, Kernel] =
        trace.kernel.map(_.pure[G]).nested

      def spanR(name: String): Resource[Nested[F, G, *], Nested[F, G, *] ~> Nested[F, G, *]] =
        Resource(
          Nested(
            trace.spanR(name).allocated.map { case (f, release) => (
              new (Nested[F, G, *] ~> Nested[F, G, *]) {
                def apply[A](fa: Nested[F, G, A]): Nested[F, G, A] =
                   Nested(f(fa.value))
              } ->
              Nested(f(release).map(_.pure[G]))
            ).pure[G] }
          )
        )

      def span[A](name: String)(k: Nested[F, G, A]): Nested[F, G, A] =
        trace.span(name)(k.value).nested

      def traceId: Nested[F, G, Option[String]] =
        trace.traceId.map(_.pure[G]).nested

      def traceUri: Nested[F, G, Option[URI]] =
        trace.traceUri.map(_.pure[G]).nested

      override def span[A](name: String, kernel: Kernel)(k: Nested[F, G, A]): Nested[F, G, A] =
        trace.span(name, kernel)(k.value).nested

      def spanR(name: String, kernel: Kernel): Resource[Nested[F, G, *], Nested[F, G, *] ~> Nested[F, G, *]] =
        Resource(
          Nested(
            trace.spanR(name, kernel).allocated.map { case (f, release) => (
              new (Nested[F, G, *] ~> Nested[F, G, *]) {
                def apply[A](fa: Nested[F, G, A]): Nested[F, G, A] =
                  Nested(f(fa.value))
              } ->
                Nested(f(release).map(_.pure[G]))
              ).pure[G] }
          )
        )
    }

  implicit def liftResource[F[_]: MonadCancelThrow](implicit trace: Trace[F]): Trace[Resource[F, *]] =
    new Trace[Resource[F, *]] {
      def put(fields: (String, TraceValue)*): Resource[F, Unit] =
        Resource.eval(trace.put(fields: _*))

      def kernel: Resource[F, Kernel] =
        Resource.eval(trace.kernel)

      def spanR(name: String): Resource[Resource[F, *], Resource[F, *] ~> Resource[F, *]] =
        Resource(
          Resource.eval(
            trace.spanR(name).allocated.map { case (f, release) =>
              new (Resource[F, *] ~> Resource[F, *]) {
                def apply[A](fa: Resource[F, A]): Resource[F, A] =
                  fa.mapK(f)
              } ->
              Resource.eval[F, Unit](f(release))
            }
          )
        )

      def span[A](name: String)(k: Resource[F, A]): Resource[F, A] =
        trace.spanR(name).flatMap { f =>
          Resource(f(k.allocated).map { case (a, release) =>
            a -> f(release)
          })
        }

      def traceId: Resource[F, Option[String]] =
        Resource.eval(trace.traceId)

      def traceUri: Resource[F, Option[URI]] =
        Resource.eval(trace.traceUri)

      /** Create a new span and add current span and kernel to parents of new span */
      def span[A](name: String, kernel: Kernel)(k: Resource[F, A]): Resource[F, A] =
        trace.spanR(name, kernel).flatMap { f =>
          Resource(f(k.allocated).map { case (a, release) =>
            a -> f(release)
          })
        }

      def spanR(name: String, kernel: Kernel): Resource[Resource[F, *], Resource[F, *] ~> Resource[F, *]] =
        Resource(
          Resource.eval(
            trace.spanR(name, kernel).allocated.map { case (f, release) =>
              new (Resource[F, *] ~> Resource[F, *]) {
                def apply[A](fa: Resource[F, A]): Resource[F, A] =
                  fa.mapK(f)
              } ->
                Resource.eval[F, Unit](f(release))
            }
          )
        )
    }

  implicit def liftStream[F[_]: MonadCancelThrow](implicit trace: Trace[F]): Trace[Stream[F, *]] =
    new Trace[Stream[F, *]] {
      def put(fields: (String, TraceValue)*): Stream[F, Unit] =
        Stream.eval(trace.put(fields: _*))

      def kernel: Stream[F, Kernel] =
        Stream.eval(trace.kernel)

      def spanR(name: String): Resource[Stream[F, *], Stream[F, *] ~> Stream[F, *]] =
        Resource(
          Stream.eval(
            trace.spanR(name).allocated.map { case (f, release) =>
              new (Stream[F, *] ~> Stream[F, *]) {
                def apply[A](fa: Stream[F, A]): Stream[F, A] =
                  fa.translate(f)
              } ->
              Stream.eval[F, Unit](f(release))
            }
          )
        )

      def span[A](name: String)(k: Stream[F, A]): Stream[F, A] =
        Stream.resource(trace.spanR(name)).flatMap(k.translate)

      def traceId: Stream[F, Option[String]] =
        Stream.eval(trace.traceId)

      def traceUri: Stream[F, Option[URI]] =
        Stream.eval(trace.traceUri)

      /** Create a new span and add current span and kernel to parents of new span */
      def span[A](name: String, kernel: Kernel)(k: Stream[F, A]): Stream[F, A] =
        Stream.resource(trace.spanR(name, kernel)).flatMap(k.translate)

      def spanR(name: String, kernel: Kernel): Resource[Stream[F, *], Stream[F, *] ~> Stream[F, *]] =
        Resource(
          Stream.eval(
            trace.spanR(name, kernel).allocated.map { case (f, release) =>
              new (Stream[F, *] ~> Stream[F, *]) {
                def apply[A](fa: Stream[F, A]): Stream[F, A] =
                  fa.translate(f)
              } ->
                Stream.eval[F, Unit](f(release))
            }
          )
        )
    }
}
