package edu.kit.varijoern;

import org.jetbrains.annotations.Nullable;
import org.prop4j.Node;

import java.util.Optional;

/**
 * Specifies which presence condition is expected to be determined for example on a specific line in a specific file.
 */
public class PresenceConditionExpectation {
    private final boolean optional;
    private final Node presenceCondition;

    /**
     * Creates a new presence condition expectation.
     *
     * @param optional          whether the presence condition is optional (see {@link #isOptional()})
     * @param presenceCondition the expected presence condition
     */
    public PresenceConditionExpectation(boolean optional, @Nullable Node presenceCondition) {
        this.optional = optional;
        this.presenceCondition = presenceCondition;
    }

    /**
     * Returns whether the presence condition is optional.
     * <p>
     * If the presence condition is optional and {@link #getPresenceCondition()} is present, the determined presence
     * condition may not be present. If it is present, it must be equivalent to the expected presence condition.
     * </p>
     * <p>
     * If the presence condition is optional and {@link #getPresenceCondition()} is empty, the determined presence
     * condition may be present or not. No further requirements are imposed, i.e. it may be any presence condition.
     * </p>
     * <p>
     * If the presence condition is not optional, the determined presence condition must be present and equivalent
     * to the expected presence condition.
     * </p>
     *
     * @return whether the presence condition is optional
     */
    public boolean isOptional() {
        return optional;
    }

    /**
     * Returns the expected presence condition.
     *
     * @return the expected presence condition
     */
    public Optional<Node> getPresenceCondition() {
        return Optional.ofNullable(presenceCondition);
    }
}
