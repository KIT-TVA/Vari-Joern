package edu.kit.varijoern.samplers;

import edu.kit.varijoern.analyzers.AnalysisResult;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Samplers return a list of feature combinations to be scanned in each iteration. Each iteration may use the analysis
 * results of previous iterations to optimize the sample for the next iteration.
 */
public interface Sampler {
    /**
     * Returns a sample of feature combinations.
     *
     * @param analysisResults the analysis results of the feature combinations of the previous iteration
     * @return a list of feature combinations
     */
    @NotNull List<List<String>> sample(List<AnalysisResult> analysisResults);
}
