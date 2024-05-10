package edu.kit.varijoern.output;

import edu.kit.varijoern.analyzers.AggregatedAnalysisResult;
import edu.kit.varijoern.analyzers.AnalysisResult;

import java.io.PrintStream;
import java.util.List;

/**
 * Formats the results of the analysis into a human-readable text format.
 */
public class TextOutputFormatter implements OutputFormatter {
    @Override
    public void printResults(List<AnalysisResult> results, AggregatedAnalysisResult aggregatedResult,
                             PrintStream outStream) {
        outStream.println("Summary:");

        for (int i = 0; i < results.size(); i++) {
            AnalysisResult analysisResult = results.get(i);
            outStream.println(analysisResult);
            if (i != results.size() - 1) {
                outStream.println();
            }
        }

        outStream.println(aggregatedResult);
    }
}
