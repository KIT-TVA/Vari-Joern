package edu.kit.varijoern.composers.conditionmapping;

import org.jetbrains.annotations.NotNull;
import org.prop4j.Node;

import java.nio.file.Path;
import java.util.Optional;

/**
 * This class implements the {@link PresenceConditionMapper} interface and always returns an empty presence condition.
 * It is used when presence condition extraction is disabled or not supported.
 */
public class EmptyPresenceConditionMapper implements PresenceConditionMapper {
    @Override
    public Optional<Node> getPresenceCondition(@NotNull Path file, int lineNumber) {
        return Optional.empty();
    }
}
