// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

sealed trait TraceValue extends Product with Serializable {
  def value: Any
}

case class StringValue(value: String)   extends TraceValue
case class BooleanValue(value: Boolean) extends TraceValue
case class NumberValue(value: Number)   extends TraceValue

