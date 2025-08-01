package edu.kit.varijoern.composers;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.samplers.Configuration;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Given a list of enabled features, composers generate a source code variant.
 */
public interface Composer {
    /**
     * Runs the composer on the source files given to the {@link Composer} instance. Implementations must not modify the
     * feature model or the source files.
     *
     * @param configuration the {@link Configuration} containing the enabled features for which the composer should
     *                      generate a variant.
     * @param destination   a {@link Path} to an existing empty directory into which the resulting code should be saved.
     *                      This path must be absolute.
     * @param featureModel  the feature model of the analyzed system
     * @return a {@link CompositionInformation} instance containing information about this composer pass
     * @throws ComposerException    if the composer failed due to invalid source code
     * @throws InterruptedException if the current thread is interrupted
     */
    @NotNull
    CompositionInformation compose(@NotNull Configuration configuration, @NotNull Path destination,
                                   @NotNull IFeatureModel featureModel)
            throws IOException, ComposerException, InterruptedException;
}
