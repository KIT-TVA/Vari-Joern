package io.joern.scanners.c

import io.joern.suites.CQueryTestSuite

class DoubleFreeTests extends CQueryTestSuite(DoubleFree) {

  "find cases where a double free occurs on a pointer" in {
    val query   = queryBundle.doubleFree()
    val results = findMatchingCalls(query)

    results shouldBe Set("double_free")
  }
}