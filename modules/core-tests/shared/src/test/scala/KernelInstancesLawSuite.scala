// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

import cats.kernel.laws.discipline.*
import munit.DisciplineSuite

class KernelInstancesLawSuite extends DisciplineSuite with Arbitraries {
  checkAll("Kernel.MonoidLaws", MonoidTests[Kernel].monoid)
  checkAll("Kernel.EqLaws", EqTests[Kernel].eqv)
}
