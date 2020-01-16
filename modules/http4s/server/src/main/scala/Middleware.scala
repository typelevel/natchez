// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez.http4s.server

import cats.implicits._
import cats.data.Kleisli
import cats.effect.Sync
import natchez._
import natchez.Tags._
import org.http4s.{Http, Request}

object Middleware {

  def tracedHttpApp[F[_]: Sync, G[_]](name: String, entryPoint: EntryPoint[F])(f: Span[F] => Http[F, G]): Http[F, G] =
    tracedHttpAppF(name, entryPoint)(span => f(span).pure[F])

  def tracedHttpAppF[F[_]: Sync, G[_]](name: String, entryPoint: EntryPoint[F])(f: Span[F] => F[Http[F, G]]): Http[F, G] =
    Kleisli { request: Request[G] =>

      val kernel = Kernel(request.headers.toList.map(h => (h.name.value, h.value)).toMap)

      entryPoint.continueOrElseRoot(name, kernel).use {
        _.span("request_received").use { s =>
          for {
            _      <- s.put(http.url(request.uri.renderString))
            _      <- s.put(http.method(request.method.renderString))
            server <- f(s)
            resp   <- server.run(request)
            _      <- s.put(http.status_code(resp.status.show))
          } yield resp
        }
      }
    }
}
