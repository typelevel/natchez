// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez.xray

import cats._
import cats.data._
import cats.effect._
import cats.effect.std.Env
import com.comcast.ip4s.{IpAddress, SocketAddress}
import natchez.Kernel

trait XRayEnvironment[F[_]] {
  def daemonAddress: F[Option[SocketAddress[IpAddress]]]
  def traceId: F[Option[String]]
  def kernelFromEnvironment: F[Kernel]
}

object XRayEnvironment {
  def apply[F[_]: XRayEnvironment]: XRayEnvironment[F] = implicitly

  @deprecated("Use overload with `Env` constraint", "0.1.5")
  def instance[F[_]](F: Sync[F]): XRayEnvironment[F] = instance(F, Env.make(F))

  implicit def instance[F[_]: Functor: Env]: XRayEnvironment[F] =
    new XRayEnvironment[F] {
      override def daemonAddress: F[Option[SocketAddress[IpAddress]]] =
        OptionT(Env[F].get("AWS_XRAY_DAEMON_ADDRESS"))
          .subflatMap(SocketAddress.fromStringIp)
          .value

      override def traceId: F[Option[String]] =
        Functor[F] match {
          // this is a hack, but it lets us defer breaking source compatibility until cats-effect 3.6,
          // when there will hopefully be a constraint similar to Env, but for retrieving system properties
          case sync: Sync[F @unchecked] =>
            OptionT(sync.delay(sys.props.get("com.amazonaws.xray.traceHeader")))
              .orElseF(Env[F].get("_X_AMZN_TRACE_ID"))(sync)
              .value
          case _ =>
            Env[F].get("_X_AMZN_TRACE_ID")
        }

      override def kernelFromEnvironment: F[Kernel] =
        OptionT(traceId)
          .subflatMap(XRaySpan.parseHeader)
          .map(_.toKernel)
          .getOrElse(Kernel(Map.empty))
    }
}
