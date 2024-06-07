package edu.kit.varijoern.analyzers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import edu.kit.varijoern.composers.PresenceConditionMapper;
import edu.kit.varijoern.composers.sourcemap.SourceMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Contains information about the weaknesses an analyzer found in a single variant.
 */
public abstract class AnalysisResult {
    private final @NotNull PresenceConditionMapper presenceConditionMapper;
    private final @NotNull SourceMap sourceMap;
    private final @NotNull Map<String, Boolean> enabledFeatures;

    protected AnalysisResult(@NotNull Map<String, Boolean> enabledFeatures,
                             @NotNull PresenceConditionMapper presenceConditionMapper,
                             @NotNull SourceMap sourceMap) {
        this.enabledFeatures = Map.copyOf(enabledFeatures);
        this.presenceConditionMapper = presenceConditionMapper;
        this.sourceMap = sourceMap;
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
     * Returns the presence condition mapper for the variant that was analyzed.
     *
     * @return the presence condition mapper
     */
    @JsonIgnore
    public @NotNull PresenceConditionMapper getPresenceConditionMapper() {
        return presenceConditionMapper;
    }

    /**
     * Returns the source map for the variant that was analyzed.
     *
     * @return the source map
     */
    @JsonIgnore
    public @NotNull SourceMap getSourceMap() {
        return sourceMap;
    }

    /**
     * Returns a list of all findings the analyzer reported.
     *
     * @return a list of all findings
     */
    public abstract @NotNull List<AnnotatedFinding> getFindings();

    @Override
    public String toString() {
        int numFindings = this.getFindings().size();
        if (numFindings == 1)
            return String.format("1 finding in variant %s", this.enabledFeatures);
        return String.format("%d findings in variant %s", numFindings, this.enabledFeatures);
    }
}
