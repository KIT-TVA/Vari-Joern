package edu.kit.varijoern.analyzers.joern;

import edu.kit.varijoern.analyzers.AnalysisResult;
import edu.kit.varijoern.composers.FeatureMapper;

import java.util.List;

/**
 * Contains information about the result of running a Joern scan.
 */
public class JoernAnalysisResult extends AnalysisResult {
    private final List<JoernFinding> findings;
    private final FeatureMapper featureMapper;

    /**
     * Creates a new {@link JoernAnalysisResult} from a list of findings.
     *
     * @param findings        the list of findings
     * @param enabledFeatures the list of features that were enabled during the analysis
     * @param featureMapper   a feature mapper for the analyzed code
     */
    public JoernAnalysisResult(List<JoernFinding> findings, List<String> enabledFeatures, FeatureMapper featureMapper) {
        super(findings.size(), enabledFeatures);
        this.findings = List.copyOf(findings);
        this.featureMapper = featureMapper;
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
            sb.append(finding.toString(this.featureMapper));
        }
        return sb.toString();
    }
}
