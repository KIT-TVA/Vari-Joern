package edu.kit.varijoern.composers;

import org.jetbrains.annotations.NotNull;
import org.prop4j.Node;

import java.nio.file.Path;
import java.util.Optional;

/**
 * An interface for determining the presence conditions of individual code lines.
 */
public interface PresenceConditionMapper {
    /**
     * Tries to determine the presence condition of the specified code line. The condition
     * may not be met by the configuration used by the composer. For example, the Antenna composer only comments
     * lines out. These lines are still present in the composed file and a (unmet) condition can be determined.
     *
     * @param file       the path to the file, relative to the output directory of the composer
     * @param lineNumber the line number in the composed file
     * @return the condition under which the line is included in the output or an empty optional if the condition could
     * not be determined
     */
    Optional<Node> getPresenceCondition(@NotNull Path file, int lineNumber);
}
