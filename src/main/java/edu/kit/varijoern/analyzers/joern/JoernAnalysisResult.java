package edu.kit.varijoern.analyzers.joern;

import edu.kit.varijoern.analyzers.AnalysisResult;

import java.util.List;

/**
 * Contains information about the result of running a Joern scan.
 */
public class JoernAnalysisResult extends AnalysisResult {
    private final List<JoernFinding> findings;

    /**
     * Creates a new {@link JoernAnalysisResult} from a list of findings.
     *
     * @param findings        the list of findings
     * @param enabledFeatures the list of features that were enabled during the analysis
     */
    public JoernAnalysisResult(List<JoernFinding> findings, List<String> enabledFeatures) {
        super(findings.size(), enabledFeatures);
        this.findings = List.copyOf(findings);
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
            sb.append(finding.toString());
        }
        return sb.toString();
    }
}
