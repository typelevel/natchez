// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package jaeger

import natchez.Kernel.HeaderKey

private[jaeger] final case class JaegerHeaderKey(key: String) extends HeaderKey
