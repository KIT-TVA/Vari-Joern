package edu.kit.varijoern.samplers;

import de.ovgu.featureide.fm.core.base.IConstraint;
import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.analyzers.AnalysisResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This sampler always returns the same sample. This sample is specified by the user in the configuration
 * file.
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
     * @param enabledFeaturesOfConfigurations the features enabled in the configuration
     * @param featureModel                    the feature model
     */
    public FixedSampler(@NotNull List<List<String>> enabledFeaturesOfConfigurations,
                        @NotNull IFeatureModel featureModel) {
        this.enabledFeaturesOfConfigurations = enabledFeaturesOfConfigurations.stream()
                .map(List::copyOf)
                .toList();
        this.featureModel = featureModel;
    }

    @Override
    public @NotNull List<Map<String, Boolean>> sample(@Nullable List<AnalysisResult<?>> analysisResults,
                                                      @NotNull Path tmpPath)
            throws SamplerException {
        List<Map<String, Boolean>> result = new ArrayList<>();
        for (List<String> enabledFeaturesOfConfiguration : this.enabledFeaturesOfConfigurations) {
            Map<String, Boolean> configuration = this.featureModel.getFeatures().stream()
                    .collect(Collectors.toMap(IFeature::getName, feature -> false));
            for (String feature : enabledFeaturesOfConfiguration) {
                if (configuration.put(feature, true) == null) {
                    throw new SamplerException("Feature %s does not exist in the feature model".formatted(feature));
                }
            }
            for (IConstraint constraint : this.featureModel.getConstraints()) {
                if (!constraint.getNode().getValue(Collections.unmodifiableMap(configuration))) {
                    throw new SamplerException("Configuration does not satisfy constraint %s".formatted(constraint));
                }
            }
            result.add(configuration);
        }
        return result;
    }
}
