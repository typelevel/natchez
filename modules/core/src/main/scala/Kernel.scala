// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

/**
 * An opaque hunk of data that can we can hand off to another system (in the form of HTTP headers),
 * which can then create new spans as children of this one. By this mechanism we allow our trace
 * to span remote calls.
 */
final case class Kernel(toHeaders: Map[String, String])
