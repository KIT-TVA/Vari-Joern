package edu.kit.varijoern.analyzers;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Contains information about the weaknesses an analyzer found in a single variant.
 */
public abstract class AnalysisResult<T extends Finding> {
    private final @NotNull Map<String, Boolean> enabledFeatures;

    protected AnalysisResult(@NotNull Map<String, Boolean> enabledFeatures) {
        this.enabledFeatures = Map.copyOf(enabledFeatures);
    }

    /**
     * Returns a map of feature names to their enabled status at the time of analysis.
     *
     * @return a map of feature names to their enabled status at the time of analysis
     */
    public @NotNull Map<String, Boolean> getEnabledFeatures() {
        return enabledFeatures;
    }

    /**
     * Returns a list of all findings the analyzer reported.
     *
     * @return a list of all findings
     */
    public abstract @NotNull List<AnnotatedFinding<T>> getFindings();

    @Override
    public String toString() {
        int numFindings = this.getFindings().size();
        if (numFindings == 1)
            return String.format("1 finding in variant %s", this.enabledFeatures);
        return String.format("%d findings in variant %s", numFindings, this.enabledFeatures);
    }
}
