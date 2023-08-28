package edu.kit.varijoern.analyzers;

/**
 * Contains information about the weaknesses an analyzer found.
 */
public abstract class AnalysisResult {
    private final int numFindings;

    protected AnalysisResult(int numFindings) {
        this.numFindings = numFindings;
    }

    /**
     * Returns the number of findings that were found during the analysis
     *
     * @return the number of findings
     */
    public int getNumFindings() {
        return numFindings;
    }
}
