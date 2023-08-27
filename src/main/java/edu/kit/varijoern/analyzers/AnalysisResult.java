package edu.kit.varijoern.analyzers;

public abstract class AnalysisResult {
    private final int numFindings;

    protected AnalysisResult(int numFindings) {
        this.numFindings = numFindings;
    }

    public int getNumFindings() {
        return numFindings;
    }
}
