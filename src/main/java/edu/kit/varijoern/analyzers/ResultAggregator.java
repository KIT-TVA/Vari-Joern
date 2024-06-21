package edu.kit.varijoern.analyzers;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A class that aggregates the results of multiple analysis runs.
 *
 * @param <T> the type of the analysis results to aggregate
 */
public abstract class ResultAggregator<T extends AnalysisResult> {
    protected final List<T> results = Collections.synchronizedList(new ArrayList<>());

    /**
     * Adds a result to the list of results. This method is thread-safe.
     *
     * @param result the result to add
     */
    public void addResult(T result) {
        results.add(result);
    }

    /**
     * Aggregates the results of multiple analysis runs, grouping them by their evidence. Implementations ensure that
     * this method is thread-safe.
     *
     * @return the aggregated results
     */
    public abstract @NotNull AggregatedAnalysisResult aggregateResults();
}
