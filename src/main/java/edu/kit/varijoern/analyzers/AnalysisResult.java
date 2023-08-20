package edu.kit.varijoern.analyzers;

public abstract class AnalysisResult {
    private final int findings;

    protected AnalysisResult(int findings) {
        this.findings = findings;
    }

    public int getFindings() {
        return findings;
    }
}
