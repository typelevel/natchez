// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.{Applicative, ~>}
import cats.effect.MonadCancel
import cats.effect.Resource

/** An entry point, for creating root spans or continuing traces that were started on another
  * system.
  */
trait EntryPoint[F[_]] {

  /** Resource that creates a new root span in a new trace. */
  final def root(name: String): Resource[F, Span[F]] = root(name, Span.Options.Defaults)

  def root(name: String, options: Span.Options): Resource[F, Span[F]]

  /** Resource that creates a new span as the child of the span specified by the given kernel,
    * which typically arrives via request headers. By this mechanism we can continue a trace that
    * began in another system. If the required headers are not present in `kernel` an exception will
    * be raised in `F`.
    */
  final def continue(name: String, kernel: Kernel): Resource[F, Span[F]] =
    continue(name, kernel, Span.Options.Defaults)

  def continue(name: String, kernel: Kernel, options: Span.Options): Resource[F, Span[F]]

  /** Resource that attempts to creates a new span as with `continue`, but falls back to a new root
    * span as with `root` if the kernel does not contain the required headers. In other words, we
    * continue the existing span if we can, otherwise we start a new one.
    */
  final def continueOrElseRoot(name: String, kernel: Kernel): Resource[F, Span[F]] =
    continueOrElseRoot(name, kernel, Span.Options.Defaults)

  def continueOrElseRoot(
      name: String,
      kernel: Kernel,
      options: Span.Options
  ): Resource[F, Span[F]]

  /** Converts this `EntryPoint[F]` to an `EntryPoint[G]` using an `F ~> G`.
    */
  def mapK[G[_]](
      f: F ~> G
  )(implicit F: MonadCancel[F, _], G: MonadCancel[G, _]): EntryPoint[G] = {
    val outer = this

    def aux(r: Resource[F, Span[F]]): Resource[G, Span[G]] = r
      .map(_.mapK(f))
      .mapK(f)

    new EntryPoint[G] {

      override def root(name: String, options: Span.Options): Resource[G, Span[G]] = aux(
        outer.root(name, options)
      )

      override def continue(
          name: String,
          kernel: Kernel,
          options: Span.Options
      ): Resource[G, Span[G]] = aux(outer.continue(name, kernel, options))

      override def continueOrElseRoot(
          name: String,
          kernel: Kernel,
          options: Span.Options
      ): Resource[G, Span[G]] = aux(outer.continueOrElseRoot(name, kernel, options))
    }
  }
}

object EntryPoint {

  def noop[F[_]: Applicative]: EntryPoint[F] = new NoopEntryPoint[F]

  private class NoopEntryPoint[F[_]: Applicative] extends EntryPoint[F] {
    override def root(name: String, options: Span.Options): Resource[F, Span[F]] =
      Resource.pure(Span.noop[F])

    override def continue(
        name: String,
        kernel: Kernel,
        options: Span.Options
    ): Resource[F, Span[F]] =
      Resource.pure(Span.noop[F])

    override def continueOrElseRoot(
        name: String,
        kernel: Kernel,
        options: Span.Options
    ): Resource[F, Span[F]] =
      Resource.pure(Span.noop[F])
  }
}
