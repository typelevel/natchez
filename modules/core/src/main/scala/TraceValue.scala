package natchez

sealed trait TraceValue extends Product with Serializable {
  def value: Any
}

case class StringValue(value: String)   extends TraceValue
case class BooleanValue(value: Boolean) extends TraceValue
case class NumberValue(value: Number)   extends TraceValue

