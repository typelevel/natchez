// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez.xray

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

private[xray] trait XRayEnvironmentCompanionPlatform {
  def env = process.env
}

@js.native
@JSImport("process", JSImport.Default)
private[xray] object process extends js.Object {
  def env: js.Dictionary[String] = js.native
}
