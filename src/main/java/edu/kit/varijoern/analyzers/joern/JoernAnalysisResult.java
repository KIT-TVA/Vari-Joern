package edu.kit.varijoern.analyzers.joern;

import edu.kit.varijoern.analyzers.AnalysisResult;
import edu.kit.varijoern.composers.FeatureMapper;
import edu.kit.varijoern.composers.sourcemap.SourceMap;

import java.util.List;
import java.util.Map;

/**
 * Contains information about the result of running a Joern scan.
 */
public class JoernAnalysisResult extends AnalysisResult {
    private final List<JoernFinding> findings;
    private final FeatureMapper featureMapper;
    private final SourceMap sourceMap;

    /**
     * Creates a new {@link JoernAnalysisResult} from a list of findings.
     *
     * @param findings        the list of findings
     * @param enabledFeatures a map of feature names to their enabled status at the time of analysis
     * @param featureMapper   a feature mapper for the analyzed code
     * @param sourceMap       a source map for the analyzed code
     */
    public JoernAnalysisResult(List<JoernFinding> findings, Map<String, Boolean> enabledFeatures,
                               FeatureMapper featureMapper, SourceMap sourceMap) {
        super(findings.size(), enabledFeatures);
        this.findings = List.copyOf(findings);
        this.featureMapper = featureMapper;
        this.sourceMap = sourceMap;
    }

    /**
     * Returns a list of all findings Joern reported.
     *
     * @return a list of all findings
     */
    public List<JoernFinding> getFindings() {
        return findings;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString());
        for (JoernFinding finding : this.findings) {
            sb.append(System.lineSeparator());
            sb.append(finding.toString(this.featureMapper, this.sourceMap));
        }
        return sb.toString();
    }
}
