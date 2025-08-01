package edu.kit.varijoern.caching;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.analyzers.AnalysisResult;
import edu.kit.varijoern.samplers.SampleTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class DummyResultCache extends ResultCache {
    @Override
    public @Nullable IFeatureModel getFeatureModel() {
        return null;
    }

    @Override
    public void cacheFeatureModel(@NotNull IFeatureModel featureModel) {
    }

    @Override
    public @Nullable List<Map<String, Boolean>> getSample(int iteration) {
        return null;
    }

    @Override
    public void cacheSample(@NotNull List<Map<String, Boolean>> sample, int iteration) {
    }

    @Override
    public <T> @Nullable T getAnalysisResult(int iteration, int configurationIndex,
                                             @NotNull SampleTracker sampleTracker, Class<T> type) {
        return null;
    }

    @Override
    public void cacheAnalysisResult(@NotNull AnalysisResult<?> result, int iteration, int configurationIndex) {
    }
}
