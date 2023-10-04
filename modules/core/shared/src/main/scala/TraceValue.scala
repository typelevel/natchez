// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

sealed trait TraceValue extends Product with Serializable {
  def value: Any
}

object TraceValue {

  case class StringValue(value: String) extends TraceValue
  case class BooleanValue(value: Boolean) extends TraceValue
  case class NumberValue(value: Number) extends TraceValue

  implicit def viaTraceableValue[A: TraceableValue](a: A): TraceValue =
    TraceableValue[A].toTraceValue(a)

  @deprecated("use `viaTraceableValue(value)`", "0.3.0")
  def stringToTraceValue(value: String): TraceValue = StringValue(value)
  @deprecated("use .viaTraceableValue(TraceableValue)", "0.3.0")
  def boolToTraceValue(value: Boolean): TraceValue = BooleanValue(value)
  @deprecated("use .viaTraceableValue(TraceableValue)", "0.3.0")
  def intToTraceValue(value: Int): TraceValue = NumberValue(value)
  @deprecated("use .viaTraceableValue(TraceableValue)", "0.3.0")
  def longToTraceValue(value: Long): TraceValue = NumberValue(value)
  @deprecated("use .viaTraceableValue(TraceableValue)", "0.3.0")
  def floatToTraceValue(value: Float): TraceValue = NumberValue(value)
  @deprecated("use .viaTraceableValue(TraceableValue)", "0.3.0")
  def doubleToTraceValue(value: Double): TraceValue = NumberValue(value)
}

/** A lawless typeclass responsible for converting a value of the type
  * parameter `A` to Natchez's `TraceValue`.
  *
  * You may want to use this to customize the formatting of a value
  * before attaching it to a span, or to support adding tracing as a
  * cross-cutting concern using aspect-oriented programming from a
  * library such as cats-tagless.
  *
  * @tparam A The type to be converted to `TraceValue`
  */
trait TraceableValue[A] { outer =>
  def toTraceValue(a: A): TraceValue

  final def contramap[B](f: B => A): TraceableValue[B] =
    (b: B) => outer.toTraceValue(f(b))
}

object TraceableValue {
  def apply[A: TraceableValue]: TraceableValue[A] = implicitly

  implicit val stringToTraceValue: TraceableValue[String] = TraceValue.StringValue(_)
  implicit val booleanToTraceValue: TraceableValue[Boolean] = TraceValue.BooleanValue(_)
  implicit val intToTraceValue: TraceableValue[Int] = TraceValue.NumberValue(_)
  implicit val longToTraceValue: TraceableValue[Long] = TraceValue.NumberValue(_)
  implicit val doubleToTraceValue: TraceableValue[Double] = TraceValue.NumberValue(_)
  implicit val floatToTraceValue: TraceableValue[Float] = TraceValue.NumberValue(_)
}
