package edu.kit.varijoern.composers.conditionmapping;

import org.jetbrains.annotations.NotNull;
import org.prop4j.Node;

import java.util.Optional;

/**
 * Implementations of this interface provide presence conditions for individual lines of the source file associated with
 * the instance.
 */
public interface OriginalLinePresenceConditionMapper {
    /**
     * Returns the presence condition for the specified line in the original source file associated with this
     * {@link OriginalLinePresenceConditionMapper}, if present. This presence condition does not take into account the
     * presence condition of the file itself.
     *
     * @param line the line number in the original source file
     * @return the presence condition for the specified line, or an empty {@link Optional} if no condition could be
     * determined
     */
    @NotNull Optional<Node> getPresenceCondition(int line);
}
