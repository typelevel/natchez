// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.effect._
import cats.data.Kleisli
import cats.implicits._
import cats.Applicative

/** A tracing effect. */
trait Trace[F[_]] {
  def setTag(key: String, value: TraceValue): F[Unit]
  def getBaggageItem(key: String): F[Option[String]]
  def setBaggageItem(key: String, value: String): F[Unit]
  def log(fields: (String, TraceValue)*): F[Unit]
  def span[A](label: String)(k: F[A]): F[A]
  def httpHeaders: F[Map[String, String]]
  def resource(label: String): Resource[F, Unit]
}

object Trace {

  def apply[F[_]](implicit ev: Trace[F]): ev.type = ev

  /**
   * A no-op `Trace` implementation is freely available for any applicative effect. This lets us add
   * a `Trace` constraint to most existing code without demanding anything new from the concrete
   * effect type.
   */
  def noopTrace[F[_]: Applicative]: Trace[F] =
    new Trace[F] {
      final val void = ().pure[F]
      final val item = Option.empty[String].pure[F]
      final val map  = Map.empty[String, String].pure[F]
      def getBaggageItem(key: String): F[Option[String]] = item
      def httpHeaders: F[Map[String,String]] = map
      def log(fields: (String, TraceValue)*): F[Unit] = void
      def setBaggageItem(key: String, value: String): F[Unit] = void
      def setTag(key: String, value: TraceValue): F[Unit] = void
      def span[A](label: String)(k: F[A]): F[A] = k
      def resource(label: String): Resource[F, Unit] = Resource.liftF(void)
    }

  /**
   * A trace instance for `Kleisli[F, Span[F], ?]`, which is the mechanism we use to introduce
   * context into our computations. We can also "lensMap" out to `Kleisli[F, E, ?]` given a lens
   * from `E` to `Span[F]`.
   */
  class KleisliTrace[F[_]: Bracket[?[_], Throwable]] extends Trace[Kleisli[F, Span[F], ?]] {
    def log(fields: (String, TraceValue)*): Kleisli[F, Span[F], Unit] =             Kleisli(_.log(fields.toMap))
    def span[A](label: String)(k: Kleisli[F, Span[F], A]): Kleisli[F, Span[F], A] = Kleisli(_.span(label).use(s => k(s)))
    def setTag(key: String, value: TraceValue): Kleisli[F, Span[F], Unit] =         Kleisli(_.setTag(key, value))
    def setBaggageItem(key: String, value: String): Kleisli[F, Span[F], Unit] =     Kleisli(_.setBaggageItem(key, value))
    def getBaggageItem(key: String): Kleisli[F, Span[F], Option[String]] =          Kleisli(_.getBaggageItem(key))
    def httpHeaders: Kleisli[F, Span[F], Map[String, String]] =                     Kleisli(_.toHttpHeaders)

    def resource(label: String): Resource[Kleisli[F, Span[F], ?], Unit] = {
      Resource.make(
        Kleisli((s: Span[F]) => s.span(label).allocated)) { case (_, free) =>
        Kleisli((_: Span[F]) => free)
      } .void
    }

    def lens[E](f: E => Span[F], g: (E, Span[F]) => E): Trace[Kleisli[F, E, ?]] =
      new Trace[Kleisli[F, E, ?]] {
        def log(fields: (String, TraceValue)*): Kleisli[F, E, Unit] =         Kleisli(e => f(e).log(fields.toMap))
        def span[A](label: String)(k: Kleisli[F, E, A]): Kleisli[F, E, A] =   Kleisli(e => f(e).span(label).use(s => k(g(e, s))))
        def setTag(key: String, value: TraceValue): Kleisli[F, E, Unit] =     Kleisli(e => f(e).setTag(key, value))
        def setBaggageItem(key: String, value: String): Kleisli[F, E, Unit] = Kleisli(e => f(e).setBaggageItem(key, value))
        def getBaggageItem(key: String): Kleisli[F, E, Option[String]] =      Kleisli(e => f(e).getBaggageItem(key))
        def httpHeaders: Kleisli[F, E, Map[String, String]] =                 Kleisli(e => f(e).toHttpHeaders)
        def resource(label: String): Resource[Kleisli[F, E, ?], Unit] =
          Resource.make(
            Kleisli((e: E) => f(e).span(label).allocated)) { case (_, free) =>
            Kleisli((_: E) => free)
          } .void
      }
  }

  implicit def kleisliInstance[F[_]: Bracket[?[_], Throwable]]: KleisliTrace[F] =
    new KleisliTrace[F]

}