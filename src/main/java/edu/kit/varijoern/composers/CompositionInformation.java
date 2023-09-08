package edu.kit.varijoern.composers;

import java.nio.file.Path;
import java.util.List;

/**
 * Contains information about a composer pass, such as the location of the resulting code.
 */
public class CompositionInformation {
    private final Path location;
    private final List<String> enabledFeatures;

    /**
     * Creates a new {@link CompositionInformation} instance.
     *
     * @param location        the location of the composed code. See {@link CompositionInformation#getLocation()}.
     * @param enabledFeatures the names of the features the code was composed with
     */
    public CompositionInformation(Path location, List<String> enabledFeatures) {
        this.location = location;
        this.enabledFeatures = enabledFeatures;
    }

    /**
     * Returns the location of the directory containing the composed code. Only source files should be present in this
     * directory and its subdirectories.
     *
     * @return the location of the directory containing the composed code
     */
    public Path getLocation() {
        return location;
    }

    /**
     * Returns the names of the features the code was composed with.
     *
     * @return the names of the enabled features
     */
    public List<String> getEnabledFeatures() {
        return this.enabledFeatures;
    }
}
