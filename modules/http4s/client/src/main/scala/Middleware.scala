// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez.http4s.client

import cats.effect.{Resource, Sync}
import natchez._
import natchez.Tags._
import org.http4s.client.Client
import org.http4s.{Header, Headers}

object Middleware {

  def spannedClient[F[_]](client: Client[F], clientName: String)(implicit F: Sync[F], S: Span[F]): Client[F] = {
    Client[F](request =>
      for {
        s      <- S.span(s"http4s-request-$clientName")
        _      <- Resource.liftF(s.put(span.kind("client")))
        _      <- Resource.liftF(s.put(http.method(request.method.name)))
        _      <- Resource.liftF(s.put(http.url(request.uri.renderString)))
        kernel <- Resource.liftF(s.kernel)
        c      <- client.run(request.transformHeaders(headers => headers ++ Headers(kernel.toHeaders.toList.map(b => Header(b._1, b._2)))))
        _      <- Resource.liftF(s.put(http.status_code(c.status.code.toString)))
      } yield c
    )
  }
}
