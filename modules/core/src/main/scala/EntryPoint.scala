// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.effect.Resource

/**
 * An entry point, for creating root spans or continuing traces that were started on another
 * system.
 */
trait EntryPoint[F[_]] {

  /** Resource that creates a new root span in a new trace. */
  def root(name: String): Resource[F, Span[F]]

  /**
   * Resource that creates a new span as the child of the span specified by the given kernel,
   * which typically arrives via request headers. By this mechanism we can continue a trace that
   * began in another system.
   * @see Span#key
   */
  def continue(name: String, kernel: Kernel): Resource[F, Span[F]]

}
