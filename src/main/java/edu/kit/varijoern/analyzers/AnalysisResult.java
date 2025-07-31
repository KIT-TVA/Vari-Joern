package edu.kit.varijoern.analyzers;

import edu.kit.varijoern.samplers.Configuration;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Contains information about the weaknesses an analyzer found in a single variant.
 */
public abstract class AnalysisResult<T extends Finding> {
    private final @NotNull Configuration configuration;

    protected AnalysisResult(@NotNull Configuration configuration) {
        this.configuration = configuration;
    }

    /**
     * Returns the configuration of the variant this result is for.
     *
     * @return the configuration of the variant
     */
    public @NotNull Configuration getConfiguration() {
        return this.configuration;
    }

    /**
     * Returns a list of all findings the analyzer reported.
     *
     * @return a list of all findings
     */
    public abstract @NotNull List<T> getFindings();

    @Override
    public String toString() {
        int numFindings = this.getFindings().size();
        if (numFindings == 1)
            return String.format("1 finding in variant %s", this.configuration.enabledFeatures());
        return String.format("%d findings in variant %s", numFindings, this.configuration.enabledFeatures());
    }
}
