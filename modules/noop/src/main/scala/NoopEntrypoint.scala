package natchez
package noop

import cats.Applicative
import cats.effect.Resource

final case class NoopEntrypoint[F[_]: Applicative]() extends EntryPoint[F] {

  override def root(name: String): Resource[F, Span[F]] = {
    Resource.liftF[F, Span[F]](Applicative[F].pure(NoopSpan()))
  }

  override def continue(
    name: String,
    kernel: Kernel
  ): Resource[F, Span[F]] =
    root(name)

  override def continueOrElseRoot(
    name: String,
    kernel: Kernel
  ): Resource[F, Span[F]] = root(name)
}