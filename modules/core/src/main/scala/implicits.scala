package natchez

object implicits {
  implicit def stringToTraceValue(value: String): TraceValue = StringValue(value)
  implicit def boolToTraceValue(value: Boolean):  TraceValue = BooleanValue(value)
  implicit def intToTraceValue(value: Int): TraceValue = NumberValue(value)
}

