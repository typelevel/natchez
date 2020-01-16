// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez.log

import io.circe.{Encoder, Json}
import io.circe.syntax._

private[log] object MapValuesToJson {
  implicit def toMapValuesToJsonSyntax[A, B: Encoder](map: Map[A, B]): MapValuesToJsonSyntax[A, B] =
    new MapValuesToJsonSyntax(map)

  class MapValuesToJsonSyntax[A, B](private val map: Map[A, B]) extends AnyVal {
    def mapValuesToJson(implicit E: Encoder[B]): Map[A, Json] =
      map.view.mapValues(_.asJson).toMap
  }
}
