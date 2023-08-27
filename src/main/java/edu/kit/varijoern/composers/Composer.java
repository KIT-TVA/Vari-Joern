package edu.kit.varijoern.composers;

import edu.kit.varijoern.IllegalFeatureNameException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface Composer {
    /**
     * Runs the composer on the source files given to the {@link Composer} instance.
     *
     * @param features    a list of enabled features
     * @param destination a {@link Path} to an existing empty directory into which the resulting code should be saved
     * @return a {@link Path} to the location of the composed code.
     * Should be {@code destination} or a subdirectory thereof.
     * @throws IllegalFeatureNameException if one of the specified features has an invalid name
     * @throws ComposerException           when the composer failed due to invalid source code
     */
    @NotNull
    CompositionInformation compose(@NotNull List<String> features, @NotNull Path destination)
        throws IllegalFeatureNameException, IOException, ComposerException;
}
