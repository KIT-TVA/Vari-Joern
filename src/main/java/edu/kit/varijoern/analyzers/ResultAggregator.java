package edu.kit.varijoern.analyzers;

import edu.kit.varijoern.caching.ResultCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A class that aggregates the results of multiple analysis runs.
 *
 * @param <T> the type of the analysis results to aggregate
 * @param <F> the type of the findings in the analysis results
 */
public abstract class ResultAggregator<T extends AnalysisResult<F>, F extends Finding> {
    protected final List<T> results = Collections.synchronizedList(new ArrayList<>());

    /**
     * Adds a result to the list of results. This method is thread-safe.
     *
     * @param result the result to add
     */
    public void addResult(T result) {
        results.add(result);
    }

    public @Nullable T tryAddResultFromCache(@NotNull ResultCache.AnalysisResultExtractor extractor) {
        T result = extractor.extract(this.getResultClass());
        if (result != null) {
            addResult(result);
        }
        return result;
    }

    protected abstract Class<T> getResultClass();

    /**
     * Aggregates the results of multiple analysis runs, grouping them by their evidence. Implementations ensure that
     * this method is thread-safe.
     *
     * @return the aggregated results
     */
    public abstract @NotNull AggregatedAnalysisResult aggregateResults();
}
