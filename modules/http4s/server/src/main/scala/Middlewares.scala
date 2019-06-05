package natchez.http4s.server

import cats.implicits._
import cats.data.Kleisli
import cats.effect.Sync
import natchez.{EntryPoint, Kernel, Trace}
import org.http4s.{Http, Request}

object Middlewares {
  import Trace._
  def tracedHttpApp[F[_]: Sync, G[_]](tracer: EntryPoint[F])(f: Trace[F] => F[Http[F, G]]): Http[F, G] =
    Kleisli { request: Request[G] =>

      //trait HeaderExtractor ???

      val kernel = Kernel(request.headers.toList.map(h => (h.name.value, h.value)).toMap) //need a way to know which headers to grab

      tracer.continue("request-received", kernel).use { span =>
        f(span).flatMap { server =>
          for {
            resp <- server.run(request)
            _ <- span.put(Tags.HTTP_STATUS)
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
