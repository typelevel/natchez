// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.kernel.laws.discipline.*
import cats.syntax.all.*
import munit.DisciplineSuite
import org.scalacheck.Prop
import org.typelevel.ci.CIString
import org.typelevel.ci.testing.arbitraries.*

class KernelInstancesLawSuite extends DisciplineSuite with Arbitraries {
  checkAll("Kernel.MonoidLaws", MonoidTests[Kernel].monoid)
  checkAll("Kernel.EqLaws", EqTests[Kernel].eqv)

  test(
    "Don't concatenate values that appear under the same key; instead, prefer the right-hand side"
  ) {
    Prop.forAll { (key: CIString, valueA: String, valueB: String) =>
      val kernelA = Kernel(Map(key -> valueA))
      val kernelB = Kernel(Map(key -> valueB))

      // make sure if the same key exists in both kernels, the right-hand side wins,
      // and the values aren't just combined
      assertEquals(kernelA |+| kernelB, kernelB)
    }
  }
}
