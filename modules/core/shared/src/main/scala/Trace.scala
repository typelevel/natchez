// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats._
import cats.data._
import cats.effect._
import cats.syntax.all._
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
  def spanR[A](name: String)(r: Resource[F, A]): Resource[F, A]

  /** Create a new span, and within it run the continuation `k`. */
  def span[A](name: String)(k: F[A]): F[A]

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

        def spanR[A](name: String)(r: Resource[IO, A]): Resource[IO, A] =
          Resource.eval(local.get).flatMap(parent =>
            parent.span(name).flatMap { child =>
              def inChild[B](io: IO[B]): IO[B] =
                local.set(child).bracket(_ => io)(_ => local.set(parent))
              Resource(inChild(r.allocated).map { case (a, release) =>
                a -> inChild(release)
              })
            }
          )

        def span[A](name: String)(k: IO[A]): IO[A] =
          local.get.flatMap { parent =>
            parent.span(name).flatMap { child =>
              Resource.make(local.set(child))(_ => local.set(parent))
            } .use { _ => k }
          }

        def traceId: IO[Option[String]] =
          local.get.flatMap(_.traceId)

        def traceUri: IO[Option[URI]] =
          local.get.flatMap(_.traceUri)

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
        def spanR[A](name: String)(r: Resource[F, A]): Resource[F, A] = r
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

    def spanR[A](name: String)(r: Resource[Kleisli[F, Span[F], *], A]): Resource[Kleisli[F, Span[F], *], A] =
      Resource.suspend(
        Kleisli((span: Span[F]) =>
          Applicative[F].pure(
            span.span(name).flatMap(
              child => r.mapK(Kleisli.applyK(child))
            ).mapK(Kleisli.liftK[F, Span[F]])
          )
        )
      )

    def span[A](name: String)(k: Kleisli[F, Span[F], A]): Kleisli[F,Span[F],A] =
      Kleisli(_.span(name).use(k.run))

    def lens[E](f: E => Span[F], g: (E, Span[F]) => E): Trace[Kleisli[F, E, *]] =
      new Trace[Kleisli[F, E, *]] {

        def kernel: Kleisli[F,E,Kernel] =
          Kleisli(e => f(e).kernel)

        def put(fields: (String, TraceValue)*): Kleisli[F,E,Unit] =
          Kleisli(e => f(e).put(fields: _*))

        def spanR[A](name: String)(r: Resource[Kleisli[F, E, *], A]): Resource[Kleisli[F, E, *], A] =
          Resource.suspend(
            Kleisli((e: E) =>
              Applicative[F].pure(
                f(e).span(name).flatMap(
                  child => r.mapK(Kleisli.applyK(g(e, child)))
                ).mapK(Kleisli.liftK[F, E])
              )
            )
          )

        def span[A](name: String)(k: Kleisli[F, E, A]): Kleisli[F, E, A] =
          Kleisli(e => f(e).span(name).use(s => k.run(g(e, s))))

        def traceId: Kleisli[F,E,Option[String]] =
          Kleisli(e => f(e).traceId)

        def traceUri: Kleisli[F,E,Option[URI]] =
          Kleisli(e => f(e).traceUri)

      }

    def traceId: Kleisli[F,Span[F],Option[String]] =
      Kleisli(_.traceId)

    def traceUri: Kleisli[F,Span[F],Option[URI]] =
      Kleisli(_.traceUri)

  }

  implicit def liftKleisli[F[_]: MonadCancelThrow, E](implicit trace: Trace[F]): Trace[Kleisli[F, E, *]] =
    new Trace[Kleisli[F, E, *]] {

      def put(fields: (String, TraceValue)*): Kleisli[F, E, Unit] =
        Kleisli.liftF(trace.put(fields: _*))

      def kernel: Kleisli[F, E, Kernel] =
        Kleisli.liftF(trace.kernel)

      def spanR[A](name: String)(r: Resource[Kleisli[F, E, *], A]): Resource[Kleisli[F, E, *], A] =
        Resource.suspend(
          Kleisli((e: E) => Applicative[F].pure(
            trace.spanR(name)(
              r.mapK(Kleisli.applyK(e))
            ).mapK(Kleisli.liftK[F, E])
          ))
        )

      def span[A](name: String)(k: Kleisli[F, E, A]): Kleisli[F, E, A] =
        Kleisli(e => trace.span[A](name)(k.run(e)))

      def traceId: Kleisli[F, E, Option[String]] =
        Kleisli.liftF(trace.traceId)

      def traceUri: Kleisli[F, E, Option[URI]] =
        Kleisli.liftF(trace.traceUri)
    }

  implicit def liftStateT[F[_]: MonadCancelThrow, S](implicit trace: Trace[F]): Trace[StateT[F, S, *]] =
    new Trace[StateT[F, S, *]] {
      def put(fields: (String, TraceValue)*): StateT[F, S, Unit] =
        StateT.liftF(trace.put(fields: _*))

      def kernel: StateT[F, S, Kernel] =
        StateT.liftF(trace.kernel)

      def spanR[A](name: String)(r: Resource[StateT[F, S, *], A]): Resource[StateT[F, S, *], A] =
        trace.spanR(name)(Resource.unit).mapK(StateT.liftK[F, S]) *> r

      def span[A](name: String)(k: StateT[F, S, A]): StateT[F, S, A] =
        StateT(s => trace.span[(S, A)](name)(k.run(s)))

      def traceId: StateT[F, S, Option[String]] =
        StateT.liftF(trace.traceId)

      def traceUri: StateT[F, S, Option[URI]] =
        StateT.liftF(trace.traceUri)
    }

  implicit def liftEitherT[F[_]: MonadCancelThrow, E](implicit trace: Trace[F]): Trace[EitherT[F, E, *]] =
    new Trace[EitherT[F, E, *]] {

      def put(fields: (String, TraceValue)*): EitherT[F, E, Unit] =
        EitherT.liftF(trace.put(fields: _*))

      def kernel: EitherT[F, E, Kernel] =
        EitherT.liftF(trace.kernel)

      def spanR[A](name: String)(r: Resource[EitherT[F, E, *], A]): Resource[EitherT[F, E, *], A] =
        trace.spanR(name)(Resource.unit).mapK(EitherT.liftK[F, E]) *> r

      def span[A](name: String)(k: EitherT[F, E, A]): EitherT[F, E, A] =
        EitherT(trace.span(name)(k.value))

      def traceId: EitherT[F, E, Option[String]] =
        EitherT.liftF(trace.traceId)

      def traceUri: EitherT[F, E, Option[URI]] =
        EitherT.liftF(trace.traceUri)
    }

  implicit def liftOptionT[F[_]: MonadCancelThrow](implicit trace: Trace[F]): Trace[OptionT[F, *]] =
    new Trace[OptionT[F, *]] {

      def put(fields: (String, TraceValue)*): OptionT[F, Unit] =
        OptionT.liftF(trace.put(fields: _*))

      def kernel: OptionT[F, Kernel] =
        OptionT.liftF(trace.kernel)

      def spanR[A](name: String)(r: Resource[OptionT[F, *], A]): Resource[OptionT[F, *], A] =
        trace.spanR(name)(Resource.unit).mapK(OptionT.liftK[F]) *> r

      def span[A](name: String)(k: OptionT[F, A]): OptionT[F, A] =
        OptionT(trace.span(name)(k.value))

      def traceId: OptionT[F, Option[String]] =
        OptionT.liftF(trace.traceId)

      def traceUri: OptionT[F, Option[URI]] =
        OptionT.liftF(trace.traceUri)
    }

  implicit def liftNested[F[_]: MonadCancelThrow, G[_]: Applicative](implicit trace: Trace[F], FG: MonadCancelThrow[Nested[F, G, *]]): Trace[Nested[F, G, *]] =
    new Trace[Nested[F, G, *]] {

      def put(fields: (String, TraceValue)*): Nested[F, G, Unit] =
        trace.put(fields: _*).map(_.pure[G]).nested

      def kernel: Nested[F, G, Kernel] =
        trace.kernel.map(_.pure[G]).nested

      def spanR[A](name: String)(r: Resource[Nested[F, G, *], A]): Resource[Nested[F, G, *], A] =
        trace.spanR(name)(Resource.unit).mapK(
          new (F ~> Nested[F, G, *]) {
            def apply[B](fa: F[B]): Nested[F, G, B] = Nested(fa.map(_.pure[G]))
          }
        ) >> r

      def span[A](name: String)(k: Nested[F, G, A]): Nested[F, G, A] =
        trace.span(name)(k.value).nested

      def traceId: Nested[F, G, Option[String]] =
        trace.traceId.map(_.pure[G]).nested

      def traceUri: Nested[F, G, Option[URI]] =
        trace.traceUri.map(_.pure[G]).nested
    }
}
