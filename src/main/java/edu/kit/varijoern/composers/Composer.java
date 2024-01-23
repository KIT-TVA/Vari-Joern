package edu.kit.varijoern.composers;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.IllegalFeatureNameException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Given a list of enabled features, composers generate a source code variant.
 */
public interface Composer {
    /**
     * Runs the composer on the source files given to the {@link Composer} instance.
     *
     * @param features     a map of feature names to their enabled status
     * @param destination  a {@link Path} to an existing empty directory into which the resulting code should be saved
     * @param tmpPath      a {@link Path} to a temporary directory that can be used by the composer
     * @param featureModel the feature model of the analyzed system
     * @return a {@link CompositionInformation} instance containing information about this composer pass
     * @throws IllegalFeatureNameException if one of the specified features has an invalid name
     * @throws ComposerException           if the composer failed due to invalid source code
     */
    @NotNull
    CompositionInformation compose(@NotNull Map<String, Boolean> features, @NotNull Path destination,
                                   @NotNull Path tmpPath, @NotNull IFeatureModel featureModel)
        throws IllegalFeatureNameException, IOException, ComposerException;
}
