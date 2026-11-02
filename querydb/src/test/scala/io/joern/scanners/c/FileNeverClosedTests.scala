package io.joern.scanners.c

import io.joern.suites.CQueryTestSuite

class FileNeverClosedTests extends CQueryTestSuite(FileNeverClosed) {

  "find cases where a file handle is not closed before returning from a function" in {
    val query   = queryBundle.fileNeverClosed()
    val results = findMatchingCalls(query)

    results shouldBe Set("file_never_closed")
  }
}