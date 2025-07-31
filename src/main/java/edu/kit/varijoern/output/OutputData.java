package edu.kit.varijoern.output;

import edu.kit.varijoern.analyzers.AggregatedAnalysisResult;
import edu.kit.varijoern.analyzers.AnalysisResult;

import java.util.List;
import java.util.Map;

/**
 * Contains the results of the analysis. This class is serialized to generate machine-readable output.
 *
 * @param configurations    the configurations used for the analysis
 * @param individualResults the individual results of the analysis, i.e., the results for each analyzed variant
 * @param aggregatedResult  the aggregated result of the analysis
 */
public record OutputData(List<Map<String, Boolean>> configurations, List<AnalysisResult<?>> individualResults,
                         AggregatedAnalysisResult aggregatedResult) {
}
