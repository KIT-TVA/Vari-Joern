package edu.kit.varijoern.analyzers;

import edu.kit.varijoern.composers.CompositionInformation;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Analyzers scan composed source code for weaknesses.
 */
public interface Analyzer {
    /**
     * Analyzes the source code referenced in the given {@link CompositionInformation}.
     *
     * @param compositionInformation information about the composer pass that generated the code to be analyzed
     * @return a summary of the weaknesses found during the analysis
     * @throws IOException              if an I/O exception occurred
     * @throws AnalyzerFailureException if the analysis failed for another reason
     * @throws InterruptedException     if the current thread is interrupted
     */
    @NotNull
    AnalysisResult<?> analyze(@NotNull CompositionInformation compositionInformation)
            throws IOException, AnalyzerFailureException, InterruptedException;
}
