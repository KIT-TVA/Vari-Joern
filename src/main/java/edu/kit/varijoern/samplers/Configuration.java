package edu.kit.varijoern.samplers;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Represents a configuration of enabled features for a variant.
 * This class is used to track configurations and assign them an index for serialization purposes.
 * The indices are typically managed by a {@link SampleTracker} instance.
 * @param enabledFeatures a map of feature names to their enabled state, where the keys are feature names
 *                        and the values are booleans indicating whether the feature is enabled (true) or
 *                        disabled (false)
 * @param index the index of this configuration
 */
public record Configuration(@NotNull Map<String, Boolean> enabledFeatures, int index) {
}
