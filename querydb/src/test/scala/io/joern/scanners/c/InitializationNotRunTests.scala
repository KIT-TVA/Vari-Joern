package io.joern.scanners.c

import io.joern.suites.CQueryTestSuite

class InitializationNotRunTests extends CQueryTestSuite(InitializationNotRun) {

  "find cases where initialization code was not executed before usage" in {
    val query   = queryBundle.initializationNotRun()
    val results = findMatchingVariables(query)

    results shouldBe Set("ptr_bad")
  }
}