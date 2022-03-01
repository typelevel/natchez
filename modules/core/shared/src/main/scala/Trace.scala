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
    IOLocal(rootSpan).map(new IOTrace(_) {})

  object Implicits {

    /**
     * A no-op `Trace` implementation is freely available for any applicative effect. This lets us add
     * a `Trace` constraint to most existing code without demanding anything new from the concrete
     * effect type.
     */
    implicit def noop[F[_]: Applicative]: Trace[F] =
      new NoopTrace[F] {}
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

      }

    def traceId: Kleisli[F,Span[F],Option[String]] =
      Kleisli(_.traceId)

    def traceUri: Kleisli[F,Span[F],Option[URI]] =
      Kleisli(_.traceUri)
      new NoopTrace[F] {}

  }

  implicit def liftKleisli[F[_], E](implicit trace: Trace[F]): Trace[Kleisli[F, E, *]] =
    new KleisliETrace[F, E] {}

  implicit def liftStateT[F[_], S](implicit F: Monad[F], trace: Trace[F]): Trace[StateT[F, S, *]] =
    new StateTTrace[F, S] {}

  implicit def liftEitherT[F[_], E](implicit F: Functor[F], trace: Trace[F]): Trace[EitherT[F, E, *]] =
    new EitherTTrace[F, E] {}

  implicit def liftOptionT[F[_]](implicit F: Functor[F], trace: Trace[F]): Trace[OptionT[F, *]] =
    new OptionTTrace[F] {}

  implicit def liftNested[F[_], G[_]](implicit F: Functor[F], G: Applicative[G], trace: Trace[F]): Trace[Nested[F, G, *]] =
    new NestedTrace[F, G] {}
}

private[natchez] abstract class IOTrace(local: IOLocal[Span[IO]]) extends Trace[IO] {

  def put(fields: (String, TraceValue)*): IO[Unit] =
    local.get.flatMap(_.put(fields: _*))

  def kernel: IO[Kernel] =
    local.get.flatMap(_.kernel)

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

private[natchez] abstract class NoopTrace[F[_]: Applicative] extends Trace[F] {
  final val void = ().pure[F]
  val kernel: F[Kernel] = Kernel(Map.empty).pure[F]
  def put(fields: (String, TraceValue)*): F[Unit] = void
  def span[A](name: String)(k: F[A]): F[A] = k
  def traceId: F[Option[String]] = none.pure[F]
  def traceUri: F[Option[URI]] = none.pure[F]
}

private[natchez] abstract class KleisliETrace[F[_], E](implicit trace: Trace[F])
  extends Trace[Kleisli[F, E, *]]
{
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
}

private[natchez] abstract class StateTTrace[F[_], S](implicit F: Monad[F], trace: Trace[F]) extends Trace[StateT[F, S, *]] {
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
}

private[natchez] abstract class EitherTTrace[F[_], E](implicit F: Functor[F], trace: Trace[F]) extends Trace[EitherT[F, E, *]] {
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
}

private[natchez] abstract class OptionTTrace[F[_]](implicit F: Functor[F], trace: Trace[F]) extends Trace[OptionT[F, *]] {
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
}

private[natchez] abstract class NestedTrace[F[_], G[_]](implicit F: Functor[F], G: Applicative[G], trace: Trace[F]) extends Trace[Nested[F, G, *]] {
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
}
