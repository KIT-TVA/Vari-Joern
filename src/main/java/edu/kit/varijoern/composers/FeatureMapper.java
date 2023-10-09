package edu.kit.varijoern.composers;

import org.prop4j.Node;

import java.nio.file.Path;
import java.util.Optional;

/**
 * An interface for determining which features need to be enabled or disabled for a line to be included by a composer.
 */
public interface FeatureMapper {
    /**
     * Tries to determine the condition under which the specified line is included in the composed result. The condition
     * may not be met by the feature combination used by the composer. For example, the Antenna composer only comments
     * lines out. These lines are still present in the composed file and a (unmet) condition can be determined.
     *
     * @param file       the path to the file, relative to the output directory of the composer
     * @param lineNumber the line number in the composed file
     * @return the condition under which the line is included in the output or an empty optional if the condition could
     * not be determined
     */
    Optional<Node> getCondition(Path file, int lineNumber);
}
