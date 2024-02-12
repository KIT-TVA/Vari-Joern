package edu.kit.varijoern.samplers;

import de.ovgu.featureide.fm.core.base.IConstraint;
import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.analyzers.AnalysisResult;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This sampler always returns a sampler containing a single feature combination. This combination is specified by the
 * user in the configuration file.
 */
public class FixedSampler implements Sampler {
    /**
     * The name of this {@link Sampler} implementation
     */
    public static final String NAME = "fixed";
    private final List<String> features;
    private final IFeatureModel featureModel;

    /**
     * Creates a new {@link FixedSampler} which always returns the specified feature combination.
     *
     * @param features     the feature combination
     * @param featureModel the feature model
     */
    public FixedSampler(List<String> features, IFeatureModel featureModel) {
        this.features = List.copyOf(features);
        this.featureModel = featureModel;
    }

    @Override
    public @NotNull List<Map<String, Boolean>> sample(List<AnalysisResult> analysisResults) throws SamplerException {
        Map<String, Boolean> result = this.featureModel.getFeatures().stream()
            .collect(Collectors.toMap(IFeature::getName, feature -> false));
        for (String feature : this.features) {
            if (result.put(feature, true) == null) {
                throw new SamplerException("Feature %s does not exist in the feature model".formatted(feature));
            }
        }
        for (IConstraint constraint : this.featureModel.getConstraints()) {
            if (!constraint.getNode().getValue(Collections.unmodifiableMap(result))) {
                throw new SamplerException("Feature combination does not satisfy constraint %s".formatted(constraint));
            }
        }
        return List.of(result);
    }
}
