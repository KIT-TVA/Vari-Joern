package io.joern.scanners.c

import io.joern.suites.CQueryTestSuite

class GlobalUseBeforeInitTests extends CQueryTestSuite(GlobalUseBeforeInit) {

  "find cases where a global variable is accessed without prior initialization" in {
    val query   = queryBundle.globalUseBeforeInit()
    val results = findMatchingCalls(query)

    results shouldBe Set("uninitialized_global")
  }
}