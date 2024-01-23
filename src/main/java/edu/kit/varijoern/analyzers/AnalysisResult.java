package edu.kit.varijoern.analyzers;

import java.util.Map;

/**
 * Contains information about the weaknesses an analyzer found.
 */
public abstract class AnalysisResult {
    private final int numFindings;
    private final Map<String, Boolean> enabledFeatures;

    protected AnalysisResult(int numFindings, Map<String, Boolean> enabledFeatures) {
        this.numFindings = numFindings;
        this.enabledFeatures = Map.copyOf(enabledFeatures);
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
     * Returns a map of feature names to their enabled status at the time of analysis.
     *
     * @return a map of feature names to their enabled status at the time of analysis
     */
    public Map<String, Boolean> getEnabledFeatures() {
        return enabledFeatures;
    }

    @Override
    public String toString() {
        if (this.numFindings == 1)
            return String.format("1 finding in variant %s", this.enabledFeatures);
        return String.format("%d findings in variant %s", this.numFindings, this.enabledFeatures);
    }
}
