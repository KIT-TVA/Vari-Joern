package edu.kit.varijoern.analyzers;

import java.util.List;

/**
 * Contains information about the weaknesses an analyzer found.
 */
public abstract class AnalysisResult {
    private final int numFindings;
    private final List<String> enabledFeatures;

    protected AnalysisResult(int numFindings, List<String> enabledFeatures) {
        this.numFindings = numFindings;
        this.enabledFeatures = List.copyOf(enabledFeatures);
    }

    /**
     * Returns the number of findings that were found during the analysis
     *
     * @return the number of findings
     */
    public int getNumFindings() {
        return numFindings;
    }

    /**
     * Returns a list containing the names of the features that were enabled during the analysis.
     *
     * @return the enabled features
     */
    public List<String> getEnabledFeatures() {
        return enabledFeatures;
    }

    @Override
    public String toString() {
        if (this.numFindings == 1)
            return String.format("1 finding in variant %s", this.enabledFeatures);
        return String.format("%d findings in variant %s", this.numFindings, this.enabledFeatures);
    }
}
