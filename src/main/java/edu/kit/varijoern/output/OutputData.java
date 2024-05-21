package edu.kit.varijoern.output;

import edu.kit.varijoern.analyzers.AggregatedAnalysisResult;
import edu.kit.varijoern.analyzers.AnalysisResult;

import java.util.List;

/**
 * Contains the results of the analysis. This class is serialized to generate machine-readable output.
 *
 * @param individualResults the individual results of the analysis, i.e., the results for each analyzed variant
 * @param aggregatedResult  the aggregated result of the analysis
 */
public record OutputData(List<AnalysisResult> individualResults, AggregatedAnalysisResult aggregatedResult) {
}
