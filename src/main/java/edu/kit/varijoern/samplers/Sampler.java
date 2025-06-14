package edu.kit.varijoern.samplers;

import edu.kit.varijoern.analyzers.AnalysisResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Samplers return a list of configurations to be scanned in each iteration. Each iteration may use the analysis results
 * of previous iterations to optimize the sample for the next iteration.
 */
public interface Sampler {
    /**
     * Returns a sample of configurations.
     *
     * @param analysisResults the analysis results of the configurations of the previous iteration
     * @param tmpPath         a path to a directory used for temporary files that are needed for sampling. Must be
     *                        absolute.
     * @return a list of configurations
     */
    @NotNull
    List<Map<String, Boolean>> sample(@Nullable List<AnalysisResult<?>> analysisResults, @NotNull Path tmpPath)
            throws SamplerException, InterruptedException, IOException;
}
