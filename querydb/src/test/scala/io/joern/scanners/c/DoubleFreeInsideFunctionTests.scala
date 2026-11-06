package io.joern.scanners.c

import io.joern.suites.CQueryTestSuite

class DoubleFreeInsideFunctionTests extends CQueryTestSuite(DoubleFreeInsideFunction) {

  "find cases where a double free occurs on a pointer inside a function" in {
    val query   = queryBundle.doubleFreeInsideFunction()
    val results = findMatchingCalls(query)

    results shouldBe Set("double_free")
  }
}