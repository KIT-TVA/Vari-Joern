package edu.kit.varijoern.analyzers;

import edu.kit.varijoern.composers.CompositionInformation;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Analyzers scan composed source code for weaknesses.
 *
 * @param <T> the type of the analysis results this analyzer produces
 */
public interface Analyzer<T extends AnalysisResult> {
    /**
     * Analyzes the source code referenced in the given {@link CompositionInformation}.
     *
     * @param compositionInformation information about the composer pass that generated the code to be analyzed
     * @return a summary of the weaknesses found during the analysis
     * @throws IOException              if an I/O exception occurred
     * @throws AnalyzerFailureException if the analysis failed for another reason
     */
    @NotNull
    T analyze(@NotNull CompositionInformation compositionInformation)
            throws IOException, AnalyzerFailureException;
}
