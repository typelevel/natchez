// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package xray

import munit.FunSuite

class XRaySuite extends FunSuite {

  val rootId = "1-5759e988-bd862e3fe1be46a994272793"
  val parentId = "53995c3f42cd8ad8"

  val noParent = "Root=1-5759e988-bd862e3fe1be46a994272793;Sampled=1"
  val parent =
    "Root=1-5759e988-bd862e3fe1be46a994272793;Parent=53995c3f42cd8ad8;Sampled=1"
  val notSampled = "Root=1-5759e988-bd862e3fe1be46a994272793;Sampled=0"

  test("header parsing") {

    assertEquals(
      XRaySpan.parseHeader(noParent),
      Some(
        XRaySpan.XRayHeader("1-5759e988-bd862e3fe1be46a994272793", None, true)
      )
    )

    assertEquals(
      XRaySpan.parseHeader(parent),
      Some(
        XRaySpan.XRayHeader(
          "1-5759e988-bd862e3fe1be46a994272793",
          Some("53995c3f42cd8ad8"),
          true
        )
      )
    )

    assertEquals(XRaySpan.parseHeader(notSampled).map(_.sampled), Some(false))
  }

  test("header encoding") {

    assertEquals(XRaySpan.encodeHeader(rootId, None, true), noParent)
    assertEquals(XRaySpan.encodeHeader(rootId, Some(parentId), true), parent)
    assertEquals(XRaySpan.encodeHeader(rootId, None, false), notSampled)
  }

}
