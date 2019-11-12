// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.{Applicative, Apply}
import cats.effect.Resource
import cats.kernel.Semigroup
import cats.syntax.apply._
import cats.syntax.semigroup._

/** An span that can be passed around and used to create child spans. */
trait Span[F[_]] {

  /** Put a sequence of fields into this span. */
  def put(fields: (String, TraceValue)*): F[Unit]

  /**
   * The kernel for this span, which can be sent as headers to remote systems, which can then
   * continue this trace by constructing spans that are children of this one.
   */
  def kernel: F[Kernel]

  /** Resource that yields a child span with the given name. */
  def span(name: String): Resource[F, Span[F]]

}

object Span extends {
  implicit def spanSemigroup[F[_]: Applicative]: Semigroup[Span[F]] = new Semigroup[Span[F]] {
    override def combine(x: Span[F], y: Span[F]): Span[F] = new Span[F] {
      override def put(fields: (String, TraceValue)*): F[Unit] = x.put(fields: _*) *> y.put(fields: _*)

      override def kernel: F[Kernel] =
        Apply[F].map2(x.kernel, y.kernel)(_ |+| _)

      override def span(name: String): Resource[F, Span[F]] =
        for {
          sx <- x.span(name)
          sy <- y.span(name)
        } yield combine(sx, sy)
    }
  }
}
