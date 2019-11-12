// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.Applicative
import cats.effect.Resource
import cats.kernel.Semigroup
import cats.syntax.semigroup._

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
   * began in another system. If the required headers are not present in `kernel` an exception will
   * be raised in `F`.
   */
  def continue(name: String, kernel: Kernel): Resource[F, Span[F]]

  /**
   * Resource that attempts to creates a new span as with `continue`, but falls back to a new root
   * span as with `root` if the kernel does not contain the required headers. In other words, we
   * continue the existing span if we can, otherwise we start a new one.
   */
  def continueOrElseRoot(name: String, kernel: Kernel): Resource[F, Span[F]]


}

object EntryPoint {
  implicit def entryPointSemigroup[F[_]: Applicative]: Semigroup[EntryPoint[F]] = new Semigroup[EntryPoint[F]] {
    private def combineSpanResources(sxResource: Resource[F, Span[F]], syResource: Resource[F, Span[F]]) =
      for {
        sx <- sxResource
        sy <- syResource
      } yield sx |+| sy

    override def combine(x: EntryPoint[F], y: EntryPoint[F]): EntryPoint[F] = new EntryPoint[F] {
      override def root(name: String): Resource[F, Span[F]] =
        combineSpanResources(x.root(name), y.root(name))

      override def continue(name: String, kernel: Kernel): Resource[F, Span[F]] =
       combineSpanResources(x.continue(name, kernel), y.continue(name, kernel))

      override def continueOrElseRoot(name: String, kernel: Kernel): Resource[F, Span[F]] =
       combineSpanResources(x.continueOrElseRoot(name, kernel), y.continueOrElseRoot(name, kernel))
    }
  }
}