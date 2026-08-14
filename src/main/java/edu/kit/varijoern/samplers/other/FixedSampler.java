package edu.kit.varijoern.samplers.other;

import de.ovgu.featureide.fm.core.base.IConstraint;
import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.analyzers.AnalysisResult;
import edu.kit.varijoern.samplers.Sampler;
import edu.kit.varijoern.samplers.SamplerException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This sampler always returns the same sample. This sample is specified by the user in the configuration file.
 */
public class FixedSampler implements Sampler {
    /**
     * The name of this {@link Sampler} implementation
     */
    public static final String NAME = "fixed";
    private final @NotNull List<List<String>> enabledFeaturesOfConfigurations;
    private final @NotNull IFeatureModel featureModel;

    /**
     * Creates a new {@link FixedSampler} which always returns the specified configurations.
     *
     * @param enabledFeaturesOfConfigurations the features ({@link List} of {@link String}s) enabled in the
     *                                        configurations ({@link List} of {@link List}s).
     * @param featureModel                    the feature model (expressed as {@link IFeatureModel}) used for
     *                                        checking whether the specified configurations conform to the constraints
     *                                        of the feature model.
     */
    public FixedSampler(@NotNull List<List<String>> enabledFeaturesOfConfigurations,
                        @NotNull IFeatureModel featureModel) {
        this.enabledFeaturesOfConfigurations = enabledFeaturesOfConfigurations.stream().map(List::copyOf).toList();
        this.featureModel = featureModel;
    }

    @Override
    public @NotNull List<Map<String, Boolean>> sample(@Nullable List<AnalysisResult<?>> analysisResults,
                                                      @NotNull Path tmpPath)
            throws SamplerException {
        List<Map<String, Boolean>> result = new ArrayList<>();

        // Iterate over selected configurations.
        for (List<String> enabledFeaturesOfConfiguration : this.enabledFeaturesOfConfigurations) {
            // Retrieve all features of the feature model and initialize their selection as false.
            Map<String, Boolean> configuration = this.featureModel.getFeatures().stream()
                    .collect(Collectors.toMap(IFeature::getName, feature -> false));

            // Iterate over the selected features for the configurations and set their selection status accordingly.
            for (String feature : enabledFeaturesOfConfiguration) {
                if (configuration.put(feature, true) == null) {
                    // Raise exception if the selected feature does not exist in the feature model.
                    throw new SamplerException("Feature %s does not exist in the feature model.".formatted(feature));
                }
            }

            // Check that the chosen configurations conforms to all the constraints of the feature model and raise an
            // exception otherwise.
            for (IConstraint constraint : this.featureModel.getConstraints()) {
                if (!constraint.getNode().getValue(Collections.unmodifiableMap(configuration))) {
                    throw new SamplerException("Configuration does not satisfy constraint %s.".formatted(constraint));
                }
            }
            result.add(configuration);
        }
        return result;
    }
}
