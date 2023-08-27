package edu.kit.varijoern.analyzers;

import edu.kit.varijoern.composers.CompositionInformation;

import java.io.IOException;

public interface Analyzer {
    AnalysisResult analyze(CompositionInformation compositionInformation) throws IOException, AnalyzerFailureException;
}
