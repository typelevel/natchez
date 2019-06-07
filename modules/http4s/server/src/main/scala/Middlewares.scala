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

object Middlewares {
  import Trace._
  import TraceValue._
  def tracedHttpApp[F[_]: Sync, G[_]](name: String, tracer: EntryPoint[F])(f: Trace[F] => F[Http[F, G]]): Http[F, G] =
    Kleisli { request: Request[G] =>

      val kernel = Kernel(request.headers.toList.map(h => (h.name.value, h.value)).toMap)

      tracer.continue(name, kernel).use { span =>

        val trace: Trace[F] = Trace[Kleisli[F, Span[F], ?]]

        f(trace).flatMap { server =>
          for {
            resp <- server.run(request)
            _ <- span.put(http.status_code(resp.status.show))
          } yield ???
        }
      }
//      val requestIds = RequestIdentification.fromRequest[F, G](request)
//
//      val baggage: F[Baggage] = requestIds.map { rid =>
//        Baggage(RequestIdentification.toHeaders(rid).toList.flatMap(Header.unapply).map(h => (h._1.value, h._2)).toMap)
//      }
//
//      baggage.flatMap { b =>
//        Span.fromHttpHeaderBaggage(tracer, "request-received", b).use { span =>
//          span.setTag(Tags.SPAN_KIND.getKey, Tags.SPAN_KIND_SERVER) >>
//          span.setTag(Tags.HTTP_METHOD.getKey, request.method.renderString) >>
//            f(span).flatMap(_(request)).flatTap { response =>
//              span.setTag(Tags.HTTP_STATUS.getKey, response.status.code.toString)
//            }
//        }
//      }
//    }
  }
}
