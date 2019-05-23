// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

object implicits {
  implicit def stringToTraceValue(value: String): TraceValue = StringValue(value)
  implicit def boolToTraceValue(value: Boolean):  TraceValue = BooleanValue(value)
  implicit def intToTraceValue(value: Int): TraceValue = NumberValue(value)
}

