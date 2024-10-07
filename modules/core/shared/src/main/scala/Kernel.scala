// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.syntax.all.*
import cats.{Eq, Monoid}
import org.typelevel.ci.*

import java.util
import scala.jdk.CollectionConverters.*

/** An opaque hunk of data that we can hand off to another system (in the form of HTTP headers),
  * which can then create new spans as children of this one. By this mechanism we allow our trace
  * to span remote calls.
  */
final case class Kernel(toHeaders: Map[CIString, String]) {
  private[natchez] def toJava: util.Map[String, String] =
    toHeaders.map { case (k, v) => k.toString -> v }.asJava
}

object Kernel {
  private[natchez] def fromJava(headers: util.Map[String, String]): Kernel =
    apply(headers.asScala.map { case (k, v) => CIString(k) -> v }.toMap)

  implicit val kernelMonoid: Monoid[Kernel] =
    Monoid.instance(Kernel(Map.empty), (a, b) => Kernel(a.toHeaders ++ b.toHeaders))

  implicit val kernelEq: Eq[Kernel] =
    Eq[Map[CIString, String]].contramap(_.toHeaders)
}
