package edu.kit.varijoern;

import java.io.IOException;
import java.nio.file.Path;

/**
 * A helper class for preparing test cases for the Kconfig/Kbuild implementation. Preparations could include building
 * of the code to ensure that the composer ignores build artifacts.
 */
public interface KconfigTestCasePreparer {
    void prepareTestCase(Path sourcePath) throws IOException;
}
