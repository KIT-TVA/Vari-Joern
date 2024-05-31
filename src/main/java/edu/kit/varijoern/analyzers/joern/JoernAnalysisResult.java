package edu.kit.varijoern.analyzers.joern;

import edu.kit.varijoern.analyzers.AnalysisResult;
import edu.kit.varijoern.analyzers.Evidence;
import edu.kit.varijoern.analyzers.AnnotatedFinding;
import edu.kit.varijoern.composers.PresenceConditionMapper;
import edu.kit.varijoern.composers.sourcemap.SourceLocation;
import edu.kit.varijoern.composers.sourcemap.SourceMap;
import org.prop4j.Node;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Contains information about the result of running a Joern scan.
 */
public class JoernAnalysisResult extends AnalysisResult {
    private final List<JoernFinding> findings;

    /**
     * Creates a new {@link JoernAnalysisResult} from a list of findings.
     *
     * @param findings                the list of findings
     * @param enabledFeatures         a map of feature names to their enabled status at the time of analysis
     * @param presenceConditionMapper a presence condition mapper for the analyzed code
     * @param sourceMap               a source map for the analyzed code
     */
    public JoernAnalysisResult(List<JoernFinding> findings, Map<String, Boolean> enabledFeatures,
                               PresenceConditionMapper presenceConditionMapper, SourceMap sourceMap) {
        super(enabledFeatures, presenceConditionMapper, sourceMap);
        this.findings = List.copyOf(findings);
    }

    /**
     * Returns a list of all findings Joern reported.
     *
     * @return a list of all findings
     */
    @Override
    public List<AnnotatedFinding> getFindings() {
        return findings.stream()
                .map(finding -> {
                    Evidence evidenceForConditionCalculation = finding.getEvidence().size() == 1
                            ? finding.getEvidence().iterator().next()
                            : null;
                    Node condition = evidenceForConditionCalculation == null
                            ? null
                            : evidenceForConditionCalculation.getCondition(this.getPresenceConditionMapper())
                            .orElse(null);
                    Set<SourceLocation> originalLocations = finding.getEvidence().stream()
                            .map(currentEvidence -> currentEvidence.getLocation()
                                    .flatMap(location -> this.getSourceMap().getOriginalLocation(location))
                                    .orElse(null)
                            )
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());
                    return new AnnotatedFinding(finding, originalLocations, condition);
                })
                .toList();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString());
        for (JoernFinding finding : this.findings) {
            sb.append(System.lineSeparator());
            sb.append(finding.toString(this.getPresenceConditionMapper(), this.getSourceMap()));
        }
        return sb.toString();
    }
}
