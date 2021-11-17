// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package xray

import cats.effect.std.Random
import cats.effect.{Clock, Concurrent, Resource}
import com.comcast.ip4s._
import fs2.io.net.Network

object XRay {

  def entryPoint[F[_] : Concurrent : Clock : Random : Network : XRayEnvironment](
      daemonAddress: SocketAddress[IpAddress] =
        SocketAddress(ip"127.0.0.1", port"2000")
  ): Resource[F, EntryPoint[F]] =
    Network[F]
      .openDatagramSocket()
      .map { socket =>
        new XRayEntryPoint[F](socket, daemonAddress)
      }

}
