package natchez
package noop

import cats.Applicative

final case class NoopTrace[F[_]: Applicative]() extends Trace[F] {

  override def put(fields: (String, TraceValue)*): F[Unit] = {
    Applicative[F].unit
  }

  override def kernel: F[Kernel] = {
    Applicative[F].pure(Kernel(Map.empty))
  }

  override def span[A](name: String)(k: F[A]): F[A] = {
    k
  }
}