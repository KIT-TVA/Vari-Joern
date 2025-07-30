package edu.kit.varijoern.caching;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.analyzers.AnalysisResult;
import edu.kit.varijoern.analyzers.Finding;
import edu.kit.varijoern.samplers.Configuration;
import edu.kit.varijoern.samplers.SampleTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public abstract class ResultCache {
    public abstract @Nullable IFeatureModel getFeatureModel();

    public abstract void cacheFeatureModel(@NotNull IFeatureModel featureModel);

    public abstract @Nullable List<Map<String, Boolean>> getSample(int iteration);

    public abstract void cacheSample(@NotNull List<Map<String, Boolean>> sample, int iteration);

    public void cacheSampleConfigurations(@NotNull List<Configuration> sample, int iteration) {
        cacheSample(sample.stream().map(Configuration::enabledFeatures).toList(), iteration);
    }

    public abstract <T> @Nullable T getAnalysisResult(int iteration, int configurationIndex,
                                                      @NotNull SampleTracker sampleTracker, Class<T> type);

    public AnalysisResultExtractor getAnalysisResultExtractor(int iteration, int configurationIndex,
                                                              @NotNull SampleTracker sampleTracker) {
        return new AnalysisResultExtractor(iteration, configurationIndex, sampleTracker);
    }

    public abstract void cacheAnalysisResult(@NotNull AnalysisResult<?> result, int iteration, int configurationIndex);

    public class AnalysisResultExtractor {
        private final int iteration;
        private final int configurationIndex;
        private final @NotNull SampleTracker sampleTracker;

        public AnalysisResultExtractor(int iteration, int configurationIndex, @NotNull SampleTracker sampleTracker) {
            this.iteration = iteration;
            this.configurationIndex = configurationIndex;
            this.sampleTracker = sampleTracker;
        }

        public <T extends AnalysisResult<F>, F extends Finding> @Nullable T extract(Class<T> type) {
            return getAnalysisResult(this.iteration, this.configurationIndex, this.sampleTracker, type);
        }
    }
}
