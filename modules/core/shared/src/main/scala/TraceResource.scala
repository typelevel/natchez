// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats._
import cats.data._
import cats.effect.{Trace => _, _}

/** Extends `Trace` with the capability to span a resource. */
trait TraceResource[F[_]] extends Trace[F] {
  /** Create a new span, and within it run the returned resource.  The
    * new span becomes the ambient span for the life of the resource.
    */
  def spanResource(name: String): Resource[F, Unit]
}

object TraceResource {

  def apply[F[_]](implicit ev: TraceResource[F]): ev.type = ev

  /** A `TraceResource` instance that uses `IOLocal` internally. */
  def ioTrace(rootSpan: Span[IO]): IO[TraceResource[IO]] =
    IOLocal(rootSpan).map { local =>
      new IOTrace(local) with TraceResource[IO] {
        def spanResource(name: String): Resource[IO, Unit] =
          Resource.eval(local.get).flatMap { parent =>
            parent.span(name).flatMap { child =>
              Resource.make(local.set(child))(_ => local.set(parent))
            }
          }
      }
    }

  object Implicits {

    /**
     * A no-op `TraceResource` implementation is freely available for
     * any applicative effect. This lets us add a `TraceResource`
     * constraint to most existing code without demanding anything new
     * from the concrete effect type.
     */
    implicit def noop[F[_]: Applicative]: TraceResource[F] =
      new NoopTrace[F] with TraceResource[F] {
        def spanResource(name: String): Resource[F, Unit] = Resource.unit
      }

  }

  implicit def liftKleisli[F[_], E](implicit F: MonadCancelThrow[F], trace: TraceResource[F]): TraceResource[Kleisli[F, E, *]] =
    new KleisliETrace[F, E] with TraceResource[Kleisli[F, E, *]] {

      def spanResource(name: String): Resource[Kleisli[F, E, *], Unit] =
        trace.spanResource(name).mapK(Kleisli.liftK[F, E])
    }

  implicit def liftStateT[F[_], S](implicit F: MonadCancelThrow[F], trace: TraceResource[F]): TraceResource[StateT[F, S, *]] =
    new StateTTrace[F, S] with TraceResource[StateT[F, S, *]] {

      def spanResource(name: String): Resource[StateT[F, S, *], Unit] =
        trace.spanResource(name).mapK(StateT.liftK[F, S])
    }

  implicit def liftEitherT[F[_], E](implicit F: MonadCancelThrow[F], trace: TraceResource[F]): TraceResource[EitherT[F, E, *]] =
    new EitherTTrace[F, E] with TraceResource[EitherT[F, E, *]] {

      def spanResource(name: String): Resource[EitherT[F, E, *], Unit] =
        trace.spanResource(name).mapK(EitherT.liftK[F, E])
    }

  implicit def liftOptionT[F[_]](implicit F: MonadCancelThrow[F], trace: TraceResource[F]): TraceResource[OptionT[F, *]] =
    new OptionTTrace[F] with TraceResource[OptionT[F, *]] {

      def spanResource(name: String): Resource[OptionT[F, *], Unit] =
        trace.spanResource(name).mapK(OptionT.liftK[F])
    }
}
