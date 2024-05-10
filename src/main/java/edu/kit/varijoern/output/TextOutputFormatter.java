package edu.kit.varijoern.output;

import edu.kit.varijoern.analyzers.AnalysisResult;

import java.io.PrintStream;

/**
 * Formats the individualResults of the analysis into a human-readable text format.
 */
public class TextOutputFormatter implements OutputFormatter {
    @Override
    public void printResults(OutputData results, PrintStream outStream) {
        outStream.println("Summary:");

        for (int i = 0; i < results.individualResults().size(); i++) {
            AnalysisResult analysisResult = results.individualResults().get(i);
            outStream.println(analysisResult);
            if (i != results.individualResults().size() - 1) {
                outStream.println();
            }
        }

        outStream.println(results.aggregatedResult());
    }
}
