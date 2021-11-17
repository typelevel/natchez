// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package xray

import cats.effect._
import cats.effect.std.Random
import cats.syntax.all._
import io.circe._
import io.circe.syntax._
import com.comcast.ip4s._
import fs2.io.net.{Datagram, DatagramSocket}
import scodec.bits.ByteVector

final class XRayEntryPoint[F[_]: Concurrent: Clock: Random](
    socket: DatagramSocket[F],
    daemonAddress: SocketAddress[IpAddress]
) extends EntryPoint[F] {

  def sendSegment(foo: JsonObject): F[Unit] = {
    val body = io.circe.Printer.noSpaces.printToByteBuffer(foo.asJson)
    val payload = XRayEntryPoint.header ++ ByteVector.apply(body)
    val datagram = Datagram(daemonAddress, fs2.Chunk.byteVector(payload))
    socket.write(datagram)
  }

  def make(span: F[XRaySpan[F]]): Resource[F, Span[F]] =
    Resource.makeCase(span)(XRaySpan.finish(_, this, _)).widen

  def root(name: String): Resource[F, Span[F]] =
    make(XRaySpan.root(name, this))

  def continue(name: String, kernel: Kernel): Resource[F, Span[F]] =
    make(XRaySpan.fromKernel(name, kernel, this))

  def continueOrElseRoot(name: String, kernel: Kernel): Resource[F, Span[F]] =
    make(XRaySpan.fromKernelOrElseRoot(name, kernel, this))
}

object XRayEntryPoint {
  val header = ByteVector
    .encodeUtf8("{\"format\": \"json\", \"version\": 1}\n")
    .getOrElse(ByteVector.empty)
}
