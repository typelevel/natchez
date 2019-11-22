// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package lightstep

import natchez.Kernel.HeaderKey

private[lightstep] final case class LightstepHeaderKey(key: String) extends HeaderKey
