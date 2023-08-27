package edu.kit.varijoern.composers;

import java.nio.file.Path;

/**
 * Contains information about a composer pass, such as the location of the resulting code.
 */
public class CompositionInformation {
    private final Path location;

    /**
     * Creates a new {@link CompositionInformation} instance.
     *
     * @param location the location of the composed code. See {@link CompositionInformation#getLocation()}.
     */
    public CompositionInformation(Path location) {
        this.location = location;
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
}
