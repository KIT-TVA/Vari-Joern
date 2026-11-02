package io.joern.scanners.c

import io.joern.suites.CQueryTestSuite

class NotInitializedTests extends CQueryTestSuite(NotInitialized) {

  "find cases where a uninitialized variable is accessed" in {
    val query   = queryBundle.notInitialized()
    val results = findMatchingCalls(query)

    results shouldBe Set("uninitialized")
  }
}