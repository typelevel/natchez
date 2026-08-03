// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package xray

import io.circe.syntax.*
import io.circe.literal.*
import munit.ScalaCheckSuite
import natchez.xray.XRaySpan.XRayException
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalacheck.Prop.*

class XRaySpanSuite extends ScalaCheckSuite {

  val genXRayException: Gen[XRayException] =
    for {
      id <- arbitrary[String]
      cause <- arbitrary[Throwable]
    } yield new XRayException(id, cause)
  implicit val arbXRayException: Arbitrary[XRayException] = Arbitrary(genXRayException)

  property("header encoding/parsing round-trip") {
    forAll { (exception: XRayException) =>
      val output = exception.asJson

      val stackTrace = exception.ex.getStackTrace.map { x =>
        json"""{
           "line": ${x.getLineNumber},
           "path": ${Option(x.getFileName)},
           "label": ${x.getMethodName}
            }"""
      }

      val expected =
        json"""{
          "fault": true,
          "cause": {
            "exceptions": [
              {
                "id": ${exception.id},
                "message": ${Option(exception.ex.getMessage).asJson},
                "type": ${exception.ex.getClass.getName},
                "stack": ${stackTrace}
              }
            ]
          }
        }"""

      assertEquals(output, expected)
    }
  }

}
