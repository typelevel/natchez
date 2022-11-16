// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package xray

import munit.ScalaCheckSuite
import org.scalacheck.Arbitrary
import org.scalacheck.Gen
import org.scalacheck.Prop._

class XRaySuite extends ScalaCheckSuite {

  val rootId = "1-5759e988-bd862e3fe1be46a994272793"
  val parentId = "53995c3f42cd8ad8"

  val noParent = "Root=1-5759e988-bd862e3fe1be46a994272793;Sampled=1"
  val parent =
    "Root=1-5759e988-bd862e3fe1be46a994272793;Parent=53995c3f42cd8ad8;Sampled=1"
  val notSampled = "Root=1-5759e988-bd862e3fe1be46a994272793;Sampled=0"

  val genTraceId = for {
    time <- Gen.buildableOfN[String, Char](8, Gen.hexChar)
    identifier <- Gen.buildableOfN[String, Char](24, Gen.hexChar)
  } yield s"1-$time-$identifier"

  val genSegmentId = Gen.buildableOfN[String, Char](16, Gen.hexChar)

  val genHeader = for {
    traceId <- genTraceId
    parentId <- Gen.option(genSegmentId)
    sampled <- Gen.prob(0.5)
  } yield XRaySpan.XRayHeader(traceId, parentId, sampled)

  implicit val arbitraryHeader: Arbitrary[XRaySpan.XRayHeader] = Arbitrary(genHeader)

  test("header parsing") {

    assertEquals(
      XRaySpan.parseHeader(noParent),
      Some(
        XRaySpan.XRayHeader(rootId, None, true)
      )
    )

    assertEquals(
      XRaySpan.parseHeader(parent),
      Some(
        XRaySpan.XRayHeader(rootId, Some(parentId), true)
      )
    )

    assertEquals(XRaySpan.parseHeader(notSampled).map(_.sampled), Some(false))
  }

  test("header encoding") {

    assertEquals(XRaySpan.encodeHeader(rootId, None, true), noParent)
    assertEquals(XRaySpan.encodeHeader(rootId, Some(parentId), true), parent)
    assertEquals(XRaySpan.encodeHeader(rootId, None, false), notSampled)
  }

  property("header encoding/parsing round-trip") {
    forAll { (header: XRaySpan.XRayHeader) =>
      val encoded = XRaySpan.encodeHeader(header.traceId, header.parentId, header.sampled)
      val parsed = XRaySpan.parseHeader(encoded)
      assertEquals(parsed, Some(header))
    }
  }

}
