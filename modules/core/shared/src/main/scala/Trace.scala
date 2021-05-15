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

  /** Create a new span, and within it run the continuation `k`. */
  def span[A](name: String)(k: F[A]): F[A]

  /**
   * A portal into the current span, which can be used elsewhere when executing code that is outside
   * the monadic scope of this span but logically a part of it.
   */
  def portal: F[Portal[F]]

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
        def span[A](name: String)(k: F[A]): F[A] = k
        def portal: F[Portal[F]] = Applicative[F].pure(new Portal[F] {
          def apply[A](fa: F[A]): F[A] = fa
        })
        def traceId: F[Option[String]] = none.pure[F]
        def traceUri: F[Option[URI]] = none.pure[F]
      }

  }

  /**
   * `Kleisli[F, Span[F], *]` is a `Trace` given `Bracket[F, Throwable]`. The instance can be
   * widened to an environment that *contains* a `Span[F]` via the `lens` method.
   */
  implicit def kleisliInstance[F[_]](implicit ev: Bracket[F, Throwable]): KleisliTrace[F] =
    new KleisliTrace[F]

  /**
   * A trace instance for `Kleisli[F, Span[F], *]`, which is the mechanism we use to introduce
   * context into our computations. We can also "lensMap" out to `Kleisli[F, E, *]` given a lens
   * from `E` to `Span[F]`.
   */
  class KleisliTrace[F[_]](implicit ev: Bracket[F, Throwable]) extends Trace[Kleisli[F, Span[F], *]] {

    def kernel: Kleisli[F, Span[F], Kernel] =
      Kleisli(_.kernel)

    def put(fields: (String, TraceValue)*): Kleisli[F, Span[F], Unit] =
      Kleisli(_.put(fields: _*))

    def span[A](name: String)(k: Kleisli[F, Span[F], A]): Kleisli[F,Span[F],A] =
      Kleisli(_.span(name).use(k.run))

    def lens[E](f: E => Span[F], g: (E, Span[F]) => E): Trace[Kleisli[F, E, *]] =
      new Trace[Kleisli[F, E, *]] {

        def kernel: Kleisli[F,E,Kernel] =
          Kleisli(e => f(e).kernel)

        def put(fields: (String, TraceValue)*): Kleisli[F,E,Unit] =
          Kleisli(e => f(e).put(fields: _*))

        def span[A](name: String)(k: Kleisli[F, E, A]): Kleisli[F, E, A] =
          Kleisli(e => f(e).span(name).use(s => k.run(g(e, s))))

        def traceId: Kleisli[F,E,Option[String]] =
          Kleisli(e => f(e).traceId)

        def traceUri: Kleisli[F,E,Option[URI]] =
          Kleisli(e => f(e).traceUri)

        def portal: ReaderT[F, E, Portal[ReaderT[F, E, *]]] = Kleisli(e =>
          Applicative[F].pure(new Portal[Kleisli[F, E, *]] {
            override def apply[A](fa: Kleisli[F, E, A]): Kleisli[F, E, A] = Kleisli.liftF(fa.run(e))
          })
        )
      }

    def traceId: Kleisli[F,Span[F],Option[String]] =
      Kleisli(_.traceId)

    def traceUri: Kleisli[F,Span[F],Option[URI]] =
      Kleisli(_.traceUri)

    def portal: Kleisli[F, Span[F], Portal[Kleisli[F, Span[F], *]]] = Kleisli((span: Span[F]) =>
      Applicative[F].pure(new Portal[Kleisli[F, Span[F], *]] {
        override def apply[A](fa: Kleisli[F, Span[F], A]): Kleisli[F, Span[F], A] = Kleisli.liftF(fa.run(span))
      }))
  }

  implicit def liftKleisli[F[_]: Functor, E](implicit trace: Trace[F]): Trace[Kleisli[F, E, *]] =
    new Trace[Kleisli[F, E, *]] {

      def put(fields: (String, TraceValue)*): Kleisli[F, E, Unit] =
        Kleisli.liftF(trace.put(fields: _*))

      def kernel: Kleisli[F, E, Kernel] =
        Kleisli.liftF(trace.kernel)

      def span[A](name: String)(k: Kleisli[F, E, A]): Kleisli[F, E, A] =
        Kleisli(e => trace.span[A](name)(k.run(e)))

      def traceId: Kleisli[F, E, Option[String]] =
        Kleisli.liftF(trace.traceId)

      def traceUri: Kleisli[F, E, Option[URI]] =
        Kleisli.liftF(trace.traceUri)

      def portal: Kleisli[F, E, Portal[Kleisli[F, E, *]]] = Kleisli.liftF(trace.portal).map { portal =>
        new Portal[ReaderT[F, E, *]] {
          def apply[A](fa: ReaderT[F, E, A]): ReaderT[F, E, A] = fa.mapK(portal)
        }
      }
    }

  implicit def liftStateT[F[_]: Monad, S](implicit trace: Trace[F]): Trace[StateT[F, S, *]] =
    new Trace[StateT[F, S, *]] {

      def put(fields: (String, TraceValue)*): StateT[F, S, Unit] =
        StateT.liftF(trace.put(fields: _*))

      def kernel: StateT[F, S, Kernel] =
        StateT.liftF(trace.kernel)

      def span[A](name: String)(k: StateT[F, S, A]): StateT[F, S, A] =
        StateT(s => trace.span[(S, A)](name)(k.run(s)))

      def traceId: StateT[F, S, Option[String]] =
        StateT.liftF(trace.traceId)

      def traceUri: StateT[F, S, Option[URI]] =
        StateT.liftF(trace.traceUri)

      def portal: StateT[F, S, Portal[StateT[F, S, *]]] =
        StateT.liftF(trace.portal).map { portal =>
          new Portal[StateT[F, S, *]] {
            def apply[A](fa: StateT[F, S, A]): StateT[F, S, A] = fa.mapK(portal)
          }
        }
    }

  implicit def liftEitherT[F[_]: Functor, E](implicit trace: Trace[F]): Trace[EitherT[F, E, *]] =
    new Trace[EitherT[F, E, *]] {

      def put(fields: (String, TraceValue)*): EitherT[F, E, Unit] =
        EitherT.liftF(trace.put(fields: _*))

      def kernel: EitherT[F, E, Kernel] =
        EitherT.liftF(trace.kernel)

      def span[A](name: String)(k: EitherT[F, E, A]): EitherT[F, E, A] =
        EitherT(trace.span(name)(k.value))

      def traceId: EitherT[F, E, Option[String]] =
        EitherT.liftF(trace.traceId)

      def traceUri: EitherT[F, E, Option[URI]] =
        EitherT.liftF(trace.traceUri)

      def portal: EitherT[F, E, Portal[EitherT[F, E, *]]] = EitherT.liftF(trace.portal).map { portal =>
        new Portal[EitherT[F, E, *]] {
          def apply[A](fa: EitherT[F, E, A]): EitherT[F, E, A] = fa.mapK(portal)
        }
      }
    }

  implicit def liftOptionT[F[_]: Functor](implicit trace: Trace[F]): Trace[OptionT[F, *]] =
    new Trace[OptionT[F, *]] {

      def put(fields: (String, TraceValue)*): OptionT[F, Unit] =
        OptionT.liftF(trace.put(fields: _*))

      def kernel: OptionT[F, Kernel] =
        OptionT.liftF(trace.kernel)

      def span[A](name: String)(k: OptionT[F, A]): OptionT[F, A] =
        OptionT(trace.span(name)(k.value))

      def traceId: OptionT[F, Option[String]] =
        OptionT.liftF(trace.traceId)

      def traceUri: OptionT[F, Option[URI]] =
        OptionT.liftF(trace.traceUri)

      def portal: OptionT[F, Portal[OptionT[F, *]]] = OptionT.liftF(trace.portal.map { portal =>
        new Portal[OptionT[F, *]] {
          def apply[A](fa: OptionT[F, A]): OptionT[F, A] = OptionT(portal(fa.value))
        }
      })
  }

  implicit def liftNested[F[_]: Functor, G[_]: Applicative](implicit trace: Trace[F]): Trace[Nested[F, G, *]] =
    new Trace[Nested[F, G, *]] {

      def put(fields: (String, TraceValue)*): Nested[F, G, Unit] =
        trace.put(fields: _*).map(_.pure[G]).nested

      def kernel: Nested[F, G, Kernel] =
        trace.kernel.map(_.pure[G]).nested

      def span[A](name: String)(k: Nested[F, G, A]): Nested[F, G, A] =
        trace.span(name)(k.value).nested

      def traceId: Nested[F, G, Option[String]] =
        trace.traceId.map(_.pure[G]).nested

      def traceUri: Nested[F, G, Option[URI]] =
        trace.traceUri.map(_.pure[G]).nested

      def portal: Nested[F, G, Portal[Nested[F, G, *]]] = trace.portal.map(_.pure[G]).nested.map { portal =>
        new Portal[Nested[F, G, *]] {
          def apply[A](fa: Nested[F, G, A]): Nested[F, G, A] = fa.mapK(portal)
        }
      }
    }
}

trait Portal[F[_]] extends (F ~> F)
