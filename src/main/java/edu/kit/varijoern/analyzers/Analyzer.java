package edu.kit.varijoern.analyzers;

import edu.kit.varijoern.composers.CompositionInformation;

import java.io.IOException;

/**
 * Analyzers scan composed source code for weaknesses.
 */
public interface Analyzer {
    /**
     * Analyzes the source code referenced in the given {@link CompositionInformation}. The result is stored to be used
     * in {@link #aggregateResults()}.
     *
     * @param compositionInformation information about the composer pass that generated the code to be analyzed
     * @return a summary of the weaknesses found during the analysis
     * @throws IOException              if an I/O exception occurred
     * @throws AnalyzerFailureException if the analysis failed for another reason
     */
    AnalysisResult analyze(CompositionInformation compositionInformation) throws IOException, AnalyzerFailureException;

    /**
     * Aggregates the results of multiple analysis runs, grouping them by their evidence.
     *
     * @return the aggregated results
     */
    AggregatedAnalysisResult aggregateResults();
}
