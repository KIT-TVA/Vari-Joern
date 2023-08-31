package edu.kit.varijoern.samplers;

import edu.kit.varijoern.analyzers.AnalysisResult;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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

    /**
     * Creates a new {@link FixedSampler} which always returns the specified feature combination.
     *
     * @param features the feature combination
     */
    public FixedSampler(List<String> features) {
        this.features = List.copyOf(features);
    }

    @Override
    public @NotNull List<List<String>> sample(List<AnalysisResult> analysisResults) {
        return List.of(features);
    }
}
