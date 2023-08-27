package edu.kit.varijoern.analyzers.joern;

import edu.kit.varijoern.analyzers.AnalysisResult;

import java.util.List;

/**
 * Contains information about the result of running a Joern scan.
 */
public class JoernAnalysisResult extends AnalysisResult {
    private final List<JoernFinding> findings;

    public JoernAnalysisResult(List<JoernFinding> findings) {
        super(findings.size());
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
}
