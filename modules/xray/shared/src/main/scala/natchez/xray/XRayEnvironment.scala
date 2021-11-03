// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez.xray

import cats.data._
import cats.effect._
import com.comcast.ip4s.{IpAddress, SocketAddress}
import natchez.Kernel

trait XRayEnvironment[F[_]] {
  def daemonAddress: F[Option[SocketAddress[IpAddress]]]
  def traceId: F[Option[String]]
  def kernelFromEnvironment: F[Kernel]
}

object XRayEnvironment {
  def apply[F[_] : XRayEnvironment]: XRayEnvironment[F] = implicitly

  implicit def instance[F[_] : Sync]: XRayEnvironment[F] = new XRayEnvironment[F] {
    override def daemonAddress: F[Option[SocketAddress[IpAddress]]] =
      OptionT(Sync[F].delay(sys.env.get("AWS_XRAY_DAEMON_ADDRESS")))
        .subflatMap(SocketAddress.fromStringIp)
        .value

    override def traceId: F[Option[String]] =
      Sync[F].delay(sys.env.get("_X_AMZN_TRACE_ID"))

    override def kernelFromEnvironment: F[Kernel] =
      OptionT(traceId)
        .map(XRaySpan.XRayHeader(_, None, sampled = true).toKernel)
        .getOrElse(Kernel(Map.empty))
  }
}
