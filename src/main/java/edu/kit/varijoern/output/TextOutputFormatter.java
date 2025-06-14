package edu.kit.varijoern.output;

import edu.kit.varijoern.analyzers.AnalysisResult;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;

/**
 * Formats the results of the analysis into a human-readable text format.
 */
public class TextOutputFormatter implements OutputFormatter {
    @Override
    public void printResults(@NotNull OutputData results, @NotNull PrintStream outStream) {
        outStream.println("Summary:");

        for (int i = 0; i < results.individualResults().size(); i++) {
            AnalysisResult<?> analysisResult = results.individualResults().get(i);
            outStream.println(analysisResult);
            if (i != results.individualResults().size() - 1) {
                outStream.println();
            }
        }

        outStream.println(results.aggregatedResult());
    }
}
