// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

/**
 * Mixin trait for exceptions that provide trace data. This allows exception data to be recorded
 * for spans that fail.
 */
trait Fields {
  def fields: Map[String, TraceValue]
}
