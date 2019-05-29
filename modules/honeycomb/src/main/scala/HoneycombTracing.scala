// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez.honeycomb

import cats.effect.Sync
import cats.implicits._
import natchez.Span
import cats.effect.Resource
import natchez.Tracer
import io.honeycomb.libhoney.LibHoney
import io.honeycomb.libhoney.Options
import scala.collection.JavaConverters._
import io.honeycomb.libhoney.ResponseObserver
import io.honeycomb.libhoney.responses.ClientRejected
import io.honeycomb.libhoney.responses.ServerAccepted
import io.honeycomb.libhoney.responses.ServerRejected
import io.honeycomb.libhoney.responses.Unknown
import java.util.logging.Logger
import java.util.logging.Level

object HoneycombTracing {

  def initial[F[_]: Sync](
    service: String,
    responseObserver: ResponseObserver = DefaultResponseObserver
  )(f: Options.Builder => F[Options]): Resource[F, Tracer[F]] =
    Resource.make {
      for {
        b <- Sync[F].delay(LibHoney.options.setGlobalFields(Map("service_name" -> service).asJava))
        o <- f(b)
        c <- Sync[F].delay(LibHoney.create(o))
        _ <- Sync[F].delay(c.addResponseObserver(responseObserver))
      } yield c
    } (c => Sync[F].delay(c.close())) map { c =>
      new Tracer[F] {

        def fromHttpHeaders(
          name: String,
          httpHeaders: Map[String,String]
        ): Resource[F,Span[F]] =
          Resource.make(HoneycombSpan.fromHttpHeaders(c, name, httpHeaders))(_.finish).widen[Span[F]]

        def root(name: String): Resource[F, Span[F]] =
          Resource.make(HoneycombSpan.root(c, name))(_.finish).widen[Span[F]]

        }
    }

  // a minimal side-effecting observer
  val DefaultResponseObserver: ResponseObserver =
    new ResponseObserver {

      val log = Logger.getLogger("HoneycombTracing")

      def onServerAccepted(serverAccepted: ServerAccepted): Unit = ()

      def onClientRejected(clientRejected: ClientRejected): Unit =
        log.log(Level.WARNING, s"ResponseObserver: ClientRejected: ${clientRejected.getReason()}", clientRejected.getException())

      def onServerRejected(serverRejected: ServerRejected): Unit =
        log.log(Level.WARNING, s"ResponseObserver: ServerRejected: ${serverRejected.getMessage()}")

      def onUnknown(unknown: Unknown): Unit =
        log.log(Level.WARNING, s"ResponseObserver: Unknown: ${unknown.getReason()}", unknown.getException())

    }

}
