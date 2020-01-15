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

  def spanR(name: String): Resource[F, Span[F]]
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
        
        //todo should probably at least have a name
        private lazy val emptySpan: Resource[F, Span[F]] = Resource.pure(new Span[F] {
          def put(fields: (String, TraceValue)*): F[Unit] = ().pure[F]
          def kernel: F[Kernel] = Kernel(Map.empty).pure[F]
          def span(name: String): Resource[F,Span[F]] = emptySpan
        })

        def spanR(name: String): Resource[F,Span[F]] = emptySpan
      }

  }

  /**
   * `Kleisli[F, Span[F], ?]` is a `Trace` given `Bracket[F, Throwable]`. The instance can be
   * widened to an environment that *contains* a `Span[F]` via the `lens` method.
   */
  implicit def kleisliInstance[F[_]: Sync]: KleisliTrace[F] =
    new KleisliTrace[F]

  /**
   * A trace instance for `Kleisli[F, Span[F], ?]`, which is the mechanism we use to introduce
   * context into our computations. We can also "lensMap" out to `Kleisli[F, E, ?]` given a lens
   * from `E` to `Span[F]`.
   */
  class KleisliTrace[F[_]: Sync] extends Trace[Kleisli[F, Span[F], ?]] {

    def kernel: Kleisli[F, Span[F], Kernel] =
      Kleisli(_.kernel)

    def put(fields: (String, TraceValue)*): Kleisli[F, Span[F], Unit] =
      Kleisli(_.put(fields: _*))

    def span[A](name: String)(k: Kleisli[F, Span[F], A]): Kleisli[F,Span[F],A] =
      spanR(name).use(_ => k)

    def spanR(name: String): Resource[Kleisli[F, Span[F], *], Span[Kleisli[F, Span[F], *]]] = lens[Span[F]](s=>s, (_:Any, s) => s).spanR(name)
    
    def lens[E](f: E => Span[F], g: (E, Span[F]) => E): Trace[Kleisli[F, E, ?]] =
      new Trace[Kleisli[F, E, ?]] {

        def kernel: Kleisli[F,E,Kernel] =
          Kleisli(e => f(e).kernel)

        def put(fields: (String, TraceValue)*): Kleisli[F,E,Unit] =
          Kleisli(e => f(e).put(fields: _*))

        def span[A](name: String)(k: Kleisli[F, E, A]): Kleisli[F, E, A] =
          Kleisli(e => f(e).span(name).use(s => k.run(g(e, s))))
        
        def spanR(name: String): Resource[Kleisli[F, E, *], Span[Kleisli[F, E, *]]] = 
          Resource.suspend {
            Kleisli[F, E, Resource[F, Span[F]]](f(_).span(name).pure[F])
              .map(_.mapK(Kleisli.liftK[F, E]).map(_.mapK(Kleisli.liftK[F, E])))
          }
      }

  }

}
