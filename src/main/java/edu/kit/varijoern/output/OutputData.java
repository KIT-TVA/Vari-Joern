package edu.kit.varijoern.output;

import edu.kit.varijoern.analyzers.AggregatedAnalysisResult;
import edu.kit.varijoern.analyzers.AnalysisResult;

import java.util.List;

public record OutputData(List<AnalysisResult> individualResults, AggregatedAnalysisResult aggregatedResult) {
}
