package edu.kit.varijoern.output;

import edu.kit.varijoern.analyzers.AggregatedAnalysisResult;
import edu.kit.varijoern.analyzers.AnalysisResult;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

/**
 * Formats the individualResults of the analysis for output.
 */
public interface OutputFormatter {
    /**
     * Prints the specified individualResults to the print stream.
     *
     * @param results   the individualResults to print
     * @param outStream the print stream to print to
     */
    void printResults(OutputData results, PrintStream outStream)
            throws IOException;
}
