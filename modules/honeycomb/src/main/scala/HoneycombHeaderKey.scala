// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package honeycomb

import natchez.Kernel.HeaderKey

private[honeycomb] final case class HoneycombHeaderKey(key: String) extends HeaderKey
