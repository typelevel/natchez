// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.effect._
import cats.data.Kleisli

trait Trace[F[_]] {
  def setTag(key: String, value: TraceValue): F[Unit]
  def getBaggageItem(key: String): F[Option[String]]
  def setBaggageItem(key: String, value: String): F[Unit]
  def log(fields: (String, TraceValue)*): F[Unit]
  def span[A](label: String)(k: F[A]): F[A]
}

object Trace {

  def apply[F[_]](implicit ev: Trace[F]): ev.type = ev

  abstract class KleisliTrace[F[_]: Bracket[?[_], Throwable]] extends Trace[Kleisli[F, Span[F], ?]] { outer =>
    def lens[E](f: E => Span[F], g: (E, Span[F]) => E): Trace[Kleisli[F, E, ?]] =
      new Trace[Kleisli[F, E, ?]] {
        def log(fields: (String, TraceValue)*): Kleisli[F, E, Unit] =
          Kleisli(e => f(e).log(fields.toMap))
        def span[A](label: String)(k: Kleisli[F, E, A]): Kleisli[F, E, A] =
          Kleisli(e => f(e).span(label).use(s => k(g(e, s))))
        def setTag(key: String, value: TraceValue): Kleisli[F, E, Unit] =
          Kleisli(e => f(e).setTag(key, value))
        def setBaggageItem(key: String, value: String): Kleisli[F, E, Unit] =
          Kleisli(e => f(e).setBaggageItem(key, value))
        def getBaggageItem(key: String): Kleisli[F, E, Option[String]] =
          Kleisli(e => f(e).getBaggageItem(key))
      }
  }

  implicit def kleisliInstance[F[_]: Bracket[?[_], Throwable]]: KleisliTrace[F] =
    new KleisliTrace[F] {
      def log(fields: (String, TraceValue)*): Kleisli[F, Span[F], Unit] =
        Kleisli(_.log(fields.toMap))
      def span[A](label: String)(k: Kleisli[F, Span[F], A]): Kleisli[F, Span[F], A] =
        Kleisli(_.span(label).use(s => k(s)))
      def setTag(key: String, value: TraceValue): Kleisli[F, Span[F], Unit] =
        Kleisli(_.setTag(key, value))
      def setBaggageItem(key: String, value: String): Kleisli[F, Span[F], Unit] =
        Kleisli(_.setBaggageItem(key, value))
      def getBaggageItem(key: String): Kleisli[F, Span[F], Option[String]] =
        Kleisli(_.getBaggageItem(key))
    }

}