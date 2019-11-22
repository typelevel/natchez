// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.kernel.Monoid
import natchez.Kernel.HeaderKey

/**
  * An opaque hunk of data that can we can hand off to another system (in the form of HTTP headers),
  * which can then create new spans as children of this one. By this mechanism we allow our trace
  * to span remote calls.
  */
final case class Kernel(keyedHeaders: Map[HeaderKey, String]) {
  def toHeaders(selector: PartialFunction[HeaderKey, String]): Map[String, String] = keyedHeaders.flatMap {
    case (k, v) => selector.lift(k).map(_ -> v)
  }
}

object Kernel {
  trait HeaderKey {
    def key: String
  }

  def fromHeaders(headers: Map[String, String])(
      toHeaderKey: String => HeaderKey) =
    Kernel(headers.map { case (k, v) => toHeaderKey(k) -> v })

  implicit def kernelMonoid: Monoid[Kernel] =
    new Monoid[Kernel] {
      override def empty: Kernel = Kernel(Map.empty)

      override def combine(x: Kernel, y: Kernel): Kernel =
        Kernel(x.keyedHeaders ++ y.keyedHeaders)
    }
}
