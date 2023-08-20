package edu.kit.varijoern.analyzers;

/**
 * Indicates that an {@link Analyzer} failed to complete an analysis.
 */
public class AnalyzerFailureException extends Exception {
    public AnalyzerFailureException() {
    }

    public AnalyzerFailureException(String message) {
        super(message);
    }

    public AnalyzerFailureException(String message, Throwable cause) {
        super(message, cause);
    }

    public AnalyzerFailureException(Throwable cause) {
        super(cause);
    }
}
