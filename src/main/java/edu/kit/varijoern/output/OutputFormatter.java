package edu.kit.varijoern.output;

import edu.kit.varijoern.analyzers.AggregatedAnalysisResult;
import edu.kit.varijoern.analyzers.AnalysisResult;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

/**
 * Formats the results of the analysis for output.
 */
public interface OutputFormatter {
    /**
     * Prints the specified results to the print stream.
     *
     * @param results          the results to print
     * @param aggregatedResult the results aggregated by the analyzer
     * @param outStream        the print stream to print to
     */
    void printResults(List<AnalysisResult> results, AggregatedAnalysisResult aggregatedResult, PrintStream outStream)
            throws IOException;
}
