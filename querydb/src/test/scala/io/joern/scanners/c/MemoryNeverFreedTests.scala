package io.joern.scanners.c

import io.joern.suites.CQueryTestSuite

class MemoryNeverFreedTests extends CQueryTestSuite(MemoryNeverFreed) {

  "find cases where a previously allocated pointer is not freed before returning from the function" in {
    val query   = queryBundle.memoryNeverFreed()
    val results = findMatchingCalls(query)

    results shouldBe Set("buffer_not_freed")
  }
}