// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.Id

object TraceValueTest {
  // should compile
  def traceValueInt() = Trace.Implicits.noop[Id].put(fields = ("foo", 1))
  def traceValueLong() = Trace.Implicits.noop[Id].put(fields = ("foo", 1L))
  def traceValueFloat() = Trace.Implicits.noop[Id].put(fields = ("foo", 1.0f))
  def traceValueDouble() = Trace.Implicits.noop[Id].put(fields = ("foo", 1.0d))
}
