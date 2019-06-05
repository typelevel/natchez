// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.effect.Resource

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
