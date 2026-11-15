// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.Id
import cats.data.*
import cats.laws.discipline.arbitrary.*
import munit.ScalaCheckSuite
import org.scalacheck.Prop

object TraceValueTest {
  // should compile
  def traceValueInt() = Trace.Implicits.noop[Id].put(fields = ("foo", 1))
  def traceValueLong() = Trace.Implicits.noop[Id].put(fields = ("foo", 1L))
  def traceValueFloat() = Trace.Implicits.noop[Id].put(fields = ("foo", 1.0f))
  def traceValueDouble() = Trace.Implicits.noop[Id].put(fields = ("foo", 1.0d))
}

class TraceableValueSpec extends ScalaCheckSuite {

  test(
    "TraceableValue[Either[String, Int]] should be a TraceValue.StringValue for Left and TraceValue.NumberValue for Right"
  ) {
    Prop.forAll { (input: Either[String, Int]) =>
      val output = TraceableValue[Either[String, Int]].toTraceValue(input)

      input match {
        case Left(l)  => assertEquals(output, TraceValue.StringValue(l))
        case Right(r) => assertEquals(output, TraceValue.NumberValue(r))
      }
    }
  }

  test(
    "TraceableValue[Validated[String, Int]] should be a TraceValue.StringValue for Invalid and TraceValue.NumberValue for Valid"
  ) {
    Prop.forAll { (input: Validated[String, Int]) =>
      val output = TraceableValue[Validated[String, Int]].toTraceValue(input)

      input match {
        case Validated.Invalid(l) => assertEquals(output, TraceValue.StringValue(l))
        case Validated.Valid(r)   => assertEquals(output, TraceValue.NumberValue(r))
      }
    }
  }

  test(
    "TraceableValue[Ior[String, Int]] should be a TraceValue.StringValue for Left and TraceValue.NumberValue for Right or Both"
  ) {
    Prop.forAll { (input: Ior[String, Int]) =>
      val output = TraceableValue[Ior[String, Int]].toTraceValue(input)

      input match {
        case Ior.Left(l)    => assertEquals(output, TraceValue.StringValue(l))
        case Ior.Right(r)   => assertEquals(output, TraceValue.NumberValue(r))
        case Ior.Both(_, r) =>
          assertEquals(output, TraceValue.NumberValue(r))
      }
    }
  }

  test("TraceableValue[(String, Int)] should be a TraceValue.NumberValue") {
    Prop.forAll { (input: (String, Int)) =>
      val output = TraceableValue[(String, Int)].toTraceValue(input)

      assertEquals(output, TraceValue.NumberValue(input._2))
    }
  }

  test("TraceableValue[Const[String, *]] should be a TraceValue.StringValue") {
    Prop.forAll { (input: Const[String, Int]) =>
      val output = TraceableValue[Const[String, Int]].toTraceValue(input)

      assertEquals(output, TraceValue.StringValue(input.getConst))
    }
  }

}
