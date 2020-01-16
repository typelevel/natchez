// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package honeycomb

import cats.effect.{ Resource, Sync }
import cats.implicits._
import io.honeycomb.libhoney._
import io.honeycomb.libhoney.responses._
import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters._

object Honeycomb {

  def entryPoint[F[_]: Sync](
    service:          String,
    responseObserver: ResponseObserver = DefaultResponseObserver
  )(f: Options.Builder => F[Options]): Resource[F, EntryPoint[F]] =
    Resource.make {
      for {
        b <- Sync[F].delay(LibHoney.options.setGlobalFields(Map("service_name" -> service).asJava))
        o <- f(b)
        c <- Sync[F].delay(LibHoney.create(o))
        _ <- Sync[F].delay(c.addResponseObserver(responseObserver))
      } yield c
    } (c => Sync[F].delay(c.close)) map { c =>
      new EntryPoint[F] {

        def continue(name: String, kernel: Kernel): Resource[F, Span[F]] =
          Resource.makeCase(HoneycombSpan.fromKernel(c, name, kernel))(HoneycombSpan.finish).widen

        def root(name: String): Resource[F, Span[F]] =
          Resource.makeCase(HoneycombSpan.root(c, name))(HoneycombSpan.finish).widen

        def continueOrElseRoot(name: String, kernel: Kernel): Resource[F,Span[F]] =
          Resource.makeCase(HoneycombSpan.fromKernelOrElseRoot(c, name, kernel))(HoneycombSpan.finish).widen

      }
    }

  // a minimal side-effecting observer
  val DefaultResponseObserver: ResponseObserver =
    new ResponseObserver {

      val log = LoggerFactory.getLogger("natchez.Honeycomb")

      def onServerAccepted(serverAccepted: ServerAccepted): Unit =
        ()

      def onClientRejected(clientRejected: ClientRejected): Unit =
        log.warn(
          s"ResponseObserver: ClientRejected: ${clientRejected.getReason}",
          clientRejected.getException
        )

      def onServerRejected(serverRejected: ServerRejected): Unit =
        log.warn(
          s"ResponseObserver: ServerRejected: ${serverRejected.getMessage}"
        )

      def onUnknown(unknown: Unknown): Unit =
        log.warn(
          s"ResponseObserver: Unknown: ${unknown.getReason}",
          unknown.getException
        )

    }

}
