// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez.opentelemetry

import cats.effect.{Async, Sync}
import cats.implicits._
import io.opentelemetry.sdk.common.CompletableResultCode

object Utils {
  case class CompletableResultCodeFailure(s: String) extends RuntimeException(s)

  // Converts an OpenTelemetry CompletableResultCode into an Async action of return type unit
  // This will raise if the action failed, but we don't get any information on the failure so we throw an empty exception
  def asyncFromCompletableResultCode[F[_]: Async](name: String, crc: CompletableResultCode): F[Unit] = {
    Async[F].async[Unit] { cb =>
      Sync[F].delay {
        crc.whenComplete(
          () => if (crc.isSuccess) {
            cb(().asRight)
          } else {
            cb(CompletableResultCodeFailure(s"The OpenTelemetry action '$name' failed").asLeft)
          }
        )
        None
      }
    }
  }
}
