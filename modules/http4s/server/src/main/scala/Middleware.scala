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

  def tracedHttpApp[F[_]: Sync, G[_]](name: String, tracer: EntryPoint[F])(f: Trace[F] => Http[F, G]): Http[F, G] =
    tracedHttpAppF(name, tracer)(trace => f(trace).pure[F])

  def tracedHttpAppF[F[_]: Sync, G[_]](name: String, tracer: EntryPoint[F])(f: Trace[F] => F[Http[F, G]]): Http[F, G] =
    Kleisli { request: Request[G] =>

      val kernel = Kernel(request.headers.toList.map(h => (h.name.value, h.value)).toMap)

      tracer.continue(name, kernel).use { span =>
        val trace = Trace.fromSpan(span)

        for {
          _      <- trace.put(http.url(request.uri.renderString))
          _      <- trace.put(http.method(request.method.renderString))
          server <- f(trace)
          resp   <- server.run(request)
          _      <- trace.put(http.status_code(resp.status.show))
        } yield resp
      }
    }
}
