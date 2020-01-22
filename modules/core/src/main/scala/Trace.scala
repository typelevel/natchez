// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats._
import cats.data._
import cats.effect._
import cats.implicits._

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
      }

  }

  /**
   * `Kleisli[F, Span[F], *]` is a `Trace` given `Bracket[F, Throwable]`. The instance can be
   * widened to an environment that *contains* a `Span[F]` via the `lens` method.
   */
  implicit def kleisliInstance[F[_]: Bracket[*[_], Throwable]]: KleisliTrace[F] =
    new KleisliTrace[F]

  /**
   * A trace instance for `Kleisli[F, Span[F], *]`, which is the mechanism we use to introduce
   * context into our computations. We can also "lensMap" out to `Kleisli[F, E, *]` given a lens
   * from `E` to `Span[F]`.
   */
  class KleisliTrace[F[_]: Bracket[*[_], Throwable]] extends Trace[Kleisli[F, Span[F], *]] {

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

      }

  }

}