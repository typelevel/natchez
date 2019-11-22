// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package opencensus

import natchez.Kernel.HeaderKey

private[opencensus] final case class OpenCensusHeaderKey(key: String) extends HeaderKey
