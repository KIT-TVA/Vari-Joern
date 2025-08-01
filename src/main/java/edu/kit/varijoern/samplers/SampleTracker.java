package edu.kit.varijoern.samplers;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tracks configurations and assigns IDs to them. These IDs can be used to reference configurations in a more
 * compact way, especially for serialization purposes.
 */
public class SampleTracker {
    private final @NotNull List<Configuration> configurations = new ArrayList<>();
    private final @NotNull Map<Map<String, Boolean>, Integer> configurationIndices = new HashMap<>();

    /**
     * Tracks a configuration and assigns it an ID. If the configuration already exists, it returns the existing
     * configuration instead of creating a new one.
     *
     * @param enabledFeatures a map of feature names to their enabled state, where the keys are feature names
     *                        and the values are booleans
     * @return the tracked configuration, which contains the enabled features and its index
     */
    public @NotNull Configuration trackConfiguration(@NotNull Map<String, Boolean> enabledFeatures) {
        if (this.configurationIndices.containsKey(enabledFeatures)) {
            int index = this.configurationIndices.get(enabledFeatures);
            return this.configurations.get(index);
        } else {
            int index = this.configurations.size();
            Configuration configuration = new Configuration(enabledFeatures, index);
            this.configurations.add(configuration);
            this.configurationIndices.put(enabledFeatures, index);
            return configuration;
        }
    }

    /**
     * Tracks a list of configurations and assigns IDs to them. See {@link #trackConfiguration(Map)} for more
     * information.
     *
     * @param enabledFeaturesList a list of maps, where each map contains feature names as keys and their enabled state
     *                            as values
     * @return a list of tracked configurations, each containing the enabled features and its index
     */
    public @NotNull List<Configuration> trackConfigurations(@NotNull List<Map<String, Boolean>> enabledFeaturesList) {
        return enabledFeaturesList.stream()
                .map(this::trackConfiguration)
                .collect(Collectors.toList());
    }

    /**
     * Returns a list of all tracked configurations, each represented as a map of feature names to their enabled state.
     *
     * @return a list of maps, where each map contains feature names as keys and their enabled state as values
     */
    public @NotNull List<Map<String, Boolean>> getConfigurations() {
        return this.configurations.stream()
                .map(Configuration::enabledFeatures)
                .collect(Collectors.toList());
    }
}
