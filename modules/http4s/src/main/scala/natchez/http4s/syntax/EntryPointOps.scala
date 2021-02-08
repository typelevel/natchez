// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez.http4s.syntax

import cats.~>
import cats.data.{ Kleisli, OptionT }
import cats.effect.Bracket
import cats.implicits._
import natchez.{ EntryPoint, Kernel, Span }
import org.http4s.HttpRoutes
import cats.effect.Resource
import cats.Defer
import natchez.TraceValue
import cats.Monad

trait EntryPointOps[F[_]] { outer =>

  def self: EntryPoint[F]

  /**
   * Given an entry point and HTTP Routes in Kleisli[F, Span[F], *] return routes in F. A new span
   * is created with the URI path as the name, either as a continuation of the incoming trace, if
   * any, or as a new root. This can likely be simplified.
   */
  def liftT(routes: HttpRoutes[Kleisli[F, Span[F], *]])(
    implicit ev: Bracket[F, Throwable]
  ): HttpRoutes[F] =
    Kleisli { req =>
      type G[A]  = Kleisli[F, Span[F], A]
      val lift   = λ[F ~> G](fa => Kleisli(_ => fa))
      val kernel = Kernel(req.headers.toList.map(h => (h.name.value -> h.value)).toMap)
      val spanR  = self.continueOrElseRoot(req.uri.path, kernel)
      OptionT {
        spanR.use { span =>
          val lower = λ[G ~> F](_(span))
          routes.run(req.mapK(lift)).mapK(lower).map(_.mapK(lower)).value
        }
      }
    }

  /**
   * Lift an `HttpRoutes`-yielding resource that consumes `Span`s into the bare effect. We do this
   * by ignoring any tracing that happens during allocation and freeing of the `HttpRoutes`
   * resource. The reasoning is that such a resource typically lives for the lifetime of the
   * application and it's of little use to keep a span open that long.
   */
  def liftR(routes: Resource[Kleisli[F, Span[F], *], HttpRoutes[Kleisli[F, Span[F], *]]])(
    implicit ev: Bracket[F, Throwable],
              d: Defer[F]
  ): Resource[F, HttpRoutes[F]] =
    routes.map(liftT).mapK(λ[Kleisli[F, Span[F], *] ~> F] { fa =>
      fa.run {
        new Span[F] {
          val kernel = Kernel(Map.empty).pure[F]
          def put(fields: (String, TraceValue)*) = Monad[F].unit
          def span(name: String) = Monad[Resource[F, *]].pure(this)
          def traceId = Monad[F].pure(None)
          def traceUri = Monad[F].pure(None)
          def spanId = Monad[F].pure(None)
        }
      }
    })

}

trait ToEntryPointOps {

  implicit def toEntryPointOps[F[_]](ep: EntryPoint[F]): EntryPointOps[F] =
    new EntryPointOps[F] {
      val self = ep
    }

}

object entrypoint extends ToEntryPointOps