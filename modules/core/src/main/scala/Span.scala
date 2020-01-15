// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.effect.Resource
import cats.effect.Bracket
import cats.Defer
import cats.Applicative
import cats.~>

/** An span that can be passed around and used to create child spans. */
trait Span[F[_]] { self =>

  /** Put a sequence of fields into this span. */
  def put(fields: (String, TraceValue)*): F[Unit]

  /**
   * The kernel for this span, which can be sent as headers to remote systems, which can then
   * continue this trace by constructing spans that are children of this one.
   */
  def kernel: F[Kernel]

  /** Resource that yields a child span with the given name. */
  def span(name: String): Resource[F, Span[F]]

  def mapK[G[_]](fk: F ~> G)(implicit b: Bracket[F, Throwable], deferG: Defer[G], applicativeg: Applicative[G]): Span[G] = new Span[G] {
    def put(fields: (String, TraceValue)*): G[Unit] = fk(self.put(fields:_*))
    def kernel: G[Kernel] = fk(self.kernel)
    def span(name: String): Resource[G,Span[G]] = self.span(name).mapK(fk).map(_.mapK(fk))
  }
}
