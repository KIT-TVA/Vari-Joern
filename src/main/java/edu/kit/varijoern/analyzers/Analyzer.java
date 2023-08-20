package edu.kit.varijoern.analyzers;

import java.io.IOException;
import java.nio.file.Path;

public interface Analyzer {
    AnalysisResult analyze(Path sourceLocation) throws IOException, AnalyzerFailureException;
}
