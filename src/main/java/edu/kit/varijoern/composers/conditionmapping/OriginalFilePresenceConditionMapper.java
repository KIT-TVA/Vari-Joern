package edu.kit.varijoern.composers.conditionmapping;

import org.jetbrains.annotations.NotNull;
import org.prop4j.Node;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Implementations of this interface provide presence conditions for original source files.
 */
public interface OriginalFilePresenceConditionMapper {
    /**
     * Returns the presence condition for the specified original source file, if present.
     *
     * @param path the path to the original source file, relative to the composer's source directory
     * @return the presence condition for the specified file, or an empty {@link Optional} if no condition could be
     * determined
     */
    @NotNull Optional<Node> getPresenceCondition(@NotNull Path path);
}
