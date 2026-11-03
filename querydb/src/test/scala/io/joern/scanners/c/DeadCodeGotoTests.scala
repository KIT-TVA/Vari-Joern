package io.joern.scanners.c

import io.joern.suites.CQueryTestSuite

class DeadCodeGotoTests extends CQueryTestSuite(DeadCodeGoto) {

  "find cases where there is dead code after a goto statement" in {
    val query   = queryBundle.deadCodeGoto()
    val results = findMatchingControlStructures(query)

    results shouldBe Set("unreachable")
  }
}