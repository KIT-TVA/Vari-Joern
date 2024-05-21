package edu.kit.varijoern.analyzers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import edu.kit.varijoern.composers.FeatureMapper;
import edu.kit.varijoern.composers.sourcemap.SourceMap;

import java.util.List;
import java.util.Map;

/**
 * Contains information about the weaknesses an analyzer found in a single variant.
 */
public abstract class AnalysisResult {
    private final FeatureMapper featureMapper;
    private final SourceMap sourceMap;
    private final Map<String, Boolean> enabledFeatures;

    protected AnalysisResult(Map<String, Boolean> enabledFeatures, FeatureMapper featureMapper, SourceMap sourceMap) {
        this.enabledFeatures = Map.copyOf(enabledFeatures);
        this.featureMapper = featureMapper;
        this.sourceMap = sourceMap;
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
     * Returns the feature mapper for the variant that was analyzed.
     *
     * @return the feature mapper
     */
    @JsonIgnore
    public FeatureMapper getFeatureMapper() {
        return featureMapper;
    }

    /**
     * Returns the source map for the variant that was analyzed.
     *
     * @return the source map
     */
    @JsonIgnore
    public SourceMap getSourceMap() {
        return sourceMap;
    }

    /**
     * Returns a list of all findings the analyzer reported.
     *
     * @return a list of all findings
     */
    public abstract List<AnnotatedFinding> getFindings();

    @Override
    public String toString() {
        int numFindings = this.getFindings().size();
        if (numFindings == 1)
            return String.format("1 finding in variant %s", this.enabledFeatures);
        return String.format("%d findings in variant %s", numFindings, this.enabledFeatures);
    }
}
