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
  /** The effect type of the spans used by this [[Trace]] instance */
  type Sp[_]
  
  def liftSp[A](spa: Sp[A]): F[A]

  val liftSpK: Sp ~> F = new (Sp ~> F) {
    def apply[A](fa: Sp[A]): F[A] = liftSp(fa)
  }

  /** Put a sequence of fields into the current span. */
  def put(fields: (String, TraceValue)*): F[Unit]

  /**
   * The kernel for the current span, which can be sent as headers to remote systems, which can
   * then continue this trace by constructing spans that are children of the current one.
   */
  def kernel: F[Kernel]

  /** Create a new span, and within it run the continuation `k`. */
  def span[A](name: String)(k: F[A]): F[A]

  /** Get the current span */
  def current: F[Span[Sp]]

  /** Run `k` within the provided span */
  def runWith[A](span: Span[Sp])(k: F[A]): F[A]

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
  type Aux[F[_], Sp0[_]] = Trace[F] {
    type Sp[A] = Sp0[A]
  }

  def apply[F[_]](implicit ev: Trace[F]): ev.type = ev

  object Implicits {

    /**
     * A no-op `Trace` implementation is freely available for any applicative effect. This lets us add
     * a `Trace` constraint to most existing code without demanding anything new from the concrete
     * effect type.
     */
    implicit def noop[F[_]: Applicative]: Trace[F] =
      new Trace[F] {
        type Sp[A] = F[A]
        def liftSp[A](fa: F[A]): F[A] = fa

        final val void = ().pure[F]
        val kernel: F[Kernel] = Kernel(Map.empty).pure[F]
        def put(fields: (String, TraceValue)*): F[Unit] = void
        def current: F[Span[F]] = NoopSpan[F]().pure[F].widen
        def runWith[A](span: Span[F])(k: F[A]): F[A] = k
        def span[A](name: String)(k: F[A]): F[A] = k
        def traceId: F[Option[String]] = none[String].pure[F]
        def traceUri: F[Option[URI]] = none[URI].pure[F]
      }

  }

  abstract class Lifted[F[_], G[_], Sp0[_]](protected val trace: Trace.Aux[F, Sp0]) extends Trace[G] {
    type Sp[A] = Sp0[A]

    protected def lift[A](fa: F[A]): G[A]

    override def liftSp[A](spa: Sp[A]): G[A] = lift(trace.liftSp(spa))

    override def put(fields: (String, TraceValue)*): G[Unit] = lift(trace.put(fields: _*))

    override def kernel: G[Kernel] = lift(trace.kernel)

    override def current: G[Span[Sp]] = lift(trace.current)

    override def traceId: G[Option[String]] = lift(trace.traceId)

    override def traceUri: G[Option[URI]] = lift(trace.traceUri)
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

    type Sp[A] = F[A]

    def kernel: Kleisli[F, Span[F], Kernel] =
      Kleisli(_.kernel)

    def put(fields: (String, TraceValue)*): Kleisli[F, Span[F], Unit] =
      Kleisli(_.put(fields: _*))

    def span[A](name: String)(k: Kleisli[F, Span[F], A]): Kleisli[F,Span[F],A] =
      Kleisli(_.span(name).use(k.run))

    def current: Kleisli[F, Span[F], Span[F]] = Kleisli.ask

    def runWith[A](span: Span[F])(k: Kleisli[F, Span[F], A]): Kleisli[F, Span[F], A] =
      Kleisli.liftF(k.run(span))

    def lens[E](f: E => Span[F], g: (E, Span[F]) => E): Trace.Aux[Kleisli[F, E, *], F] =
      new Trace[Kleisli[F, E, *]] {

        type Sp[A] = F[A]

        def kernel: Kleisli[F,E,Kernel] =
          Kleisli(e => f(e).kernel)

        def put(fields: (String, TraceValue)*): Kleisli[F,E,Unit] =
          Kleisli(e => f(e).put(fields: _*))

        def span[A](name: String)(k: Kleisli[F, E, A]): Kleisli[F, E, A] =
          Kleisli(e => f(e).span(name).use(s => k.run(g(e, s))))

        def current: Kleisli[F, E, Span[F]] = Kleisli(e => f(e).pure[F])

        def runWith[A](span: Span[Sp])(k: Kleisli[F, E, A]): Kleisli[F, E, A] =
          Kleisli(e => k.run(g(e, span)))

        def traceId: Kleisli[F,E,Option[String]] =
          Kleisli(e => f(e).traceId)

        def traceUri: Kleisli[F,E,Option[URI]] =
          Kleisli(e => f(e).traceUri)

        def liftSp[A](spa: F[A]): Kleisli[F, E, A] = Kleisli.liftF(spa)
      }

    def traceId: Kleisli[F,Span[F],Option[String]] =
      Kleisli(_.traceId)

    def traceUri: Kleisli[F,Span[F],Option[URI]] =
      Kleisli(_.traceUri)

    def liftSp[A](spa: F[A]): Kleisli[F, Span[F], A] = Kleisli.liftF(spa)
  }

  implicit def liftKleisli[F[_], E](implicit t: Trace[F]): Trace.Aux[Kleisli[F, E, *], t.Sp] =
    new Lifted[F, Kleisli[F, E, *], t.Sp](t) {

      protected def lift[A](fa: F[A]): Kleisli[F, E, A] = Kleisli.liftF(fa)

      def span[A](name: String)(k: Kleisli[F, E, A]): Kleisli[F, E, A] =
        Kleisli(e => trace.span[A](name)(k.run(e)))

      def runWith[A](span: Span[Sp])(k: Kleisli[F, E, A]): Kleisli[F, E, A] =
        Kleisli(e => trace.runWith(span)(k.run(e)))
    }

  implicit def liftStateT[F[_]: Monad, S](implicit t: Trace[F]): Trace.Aux[StateT[F, S, *], t.Sp] =
    new Lifted[F, StateT[F, S, *], t.Sp](t) {

      protected def lift[A](fa: F[A]): StateT[F, S, A] = StateT.liftF(fa)

      def span[A](name: String)(k: StateT[F, S, A]): StateT[F, S, A] =
        StateT(s => t.span[(S, A)](name)(k.run(s)))

      def runWith[A](span: Span[t.Sp])(k: StateT[F, S, A]): StateT[F, S, A] =
        StateT(s => t.runWith(span)(k.run(s)))
    }

  implicit def liftEitherT[F[_]: Functor, E](implicit t: Trace[F]): Trace.Aux[EitherT[F, E, *], t.Sp] =
    new Lifted[F, EitherT[F, E, *], t.Sp](t) {
      protected def lift[A](fa: F[A]): EitherT[F, E, A] = EitherT.liftF(fa)

      def span[A](name: String)(k: EitherT[F, E, A]): EitherT[F, E, A] =
        EitherT(trace.span(name)(k.value))

      def runWith[A](span: Span[trace.Sp])(k: EitherT[F, E, A]): EitherT[F, E, A] =
        EitherT(trace.runWith(span)(k.value))
    }

  implicit def liftOptionT[F[_]: Functor](implicit t: Trace[F]): Trace.Aux[OptionT[F, *], t.Sp] =
    new Lifted[F, OptionT[F, *], t.Sp](t) {
      protected def lift[A](fa: F[A]): OptionT[F, A] = OptionT.liftF(fa)

      def span[A](name: String)(k: OptionT[F, A]): OptionT[F, A] =
        OptionT(t.span(name)(k.value))

      def runWith[A](span: Span[t.Sp])(k: OptionT[F, A]): OptionT[F, A] =
        OptionT(t.runWith(span)(k.value))
    }

  implicit def liftNested[F[_]: Functor, G[_]: Applicative](implicit t: Trace[F]): Trace.Aux[Nested[F, G, *], t.Sp] =
    new Lifted[F, Nested[F, G, *], t.Sp](t) {

      protected def lift[A](fa: F[A]): Nested[F, G, A] = fa.map(_.pure[G]).nested

      def span[A](name: String)(k: Nested[F, G, A]): Nested[F, G, A] =
        t.span(name)(k.value).nested

      def runWith[A](span: Span[t.Sp])(k: Nested[F, G, A]): Nested[F, G, A] =
        t.runWith(span)(k.value).nested
    }
}
