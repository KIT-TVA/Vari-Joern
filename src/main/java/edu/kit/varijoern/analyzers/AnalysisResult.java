package edu.kit.varijoern.analyzers;

import java.util.List;
import java.util.Map;

/**
 * Contains information about the weaknesses an analyzer found.
 */
public abstract class AnalysisResult {
    private final Map<String, Boolean> enabledFeatures;

    protected AnalysisResult(Map<String, Boolean> enabledFeatures) {
        this.enabledFeatures = Map.copyOf(enabledFeatures);
    }

    /**
     * Returns a map of feature names to their enabled status at the time of analysis.
     *
     * @return a map of feature names to their enabled status at the time of analysis
     */
    public Map<String, Boolean> getEnabledFeatures() {
        return enabledFeatures;
    }

    /**
     * Returns a list of all findings the analyzer reported.
     *
     * @return a list of all findings
     */
    public abstract List<? extends Finding> getFindings();

    @Override
    public String toString() {
        int numFindings = this.getFindings().size();
        if (numFindings == 1)
            return String.format("1 finding in variant %s", this.enabledFeatures);
        return String.format("%d findings in variant %s", numFindings, this.enabledFeatures);
    }
}
