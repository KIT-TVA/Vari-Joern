package edu.kit.varijoern.analyzers.joern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.kit.varijoern.analyzers.AnalysisResult;
import edu.kit.varijoern.analyzers.AnnotatedFinding;
import edu.kit.varijoern.analyzers.Evidence;
import edu.kit.varijoern.composers.PresenceConditionMapper;
import edu.kit.varijoern.composers.sourcemap.SourceLocation;
import edu.kit.varijoern.composers.sourcemap.SourceMap;
import org.jetbrains.annotations.NotNull;
import org.prop4j.Node;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Contains information about the result of running a Joern scan.
 */
public class JoernAnalysisResult extends AnalysisResult<JoernFinding> {
    private final List<AnnotatedFinding<JoernFinding>> annotatedFindings;

    /**
     * Creates a new {@link JoernAnalysisResult} from a list of findings.
     *
     * @param findings                the list of findings
     * @param enabledFeatures         a map of feature names to their enabled status at the time of analysis
     * @param presenceConditionMapper a presence condition mapper for the analyzed code
     * @param sourceMap               a source map for the analyzed code
     */
    public JoernAnalysisResult(@NotNull List<JoernFinding> findings, @NotNull Map<String, Boolean> enabledFeatures,
                               @NotNull PresenceConditionMapper presenceConditionMapper, @NotNull SourceMap sourceMap) {
        super(enabledFeatures);
        annotatedFindings = findings.stream()
                .map(finding -> {
                    Evidence evidenceForConditionCalculation = finding.getEvidence().size() == 1
                            ? finding.getEvidence().iterator().next()
                            : null;
                    Node condition = evidenceForConditionCalculation == null
                            ? null
                            : evidenceForConditionCalculation.getCondition(presenceConditionMapper)
                            .orElse(null);
                    Set<SourceLocation> originalLocations = finding.getEvidence().stream()
                            .map(currentEvidence -> currentEvidence.resolveLocation(sourceMap)
                                    .orElse(null)
                            )
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());
                    return new AnnotatedFinding<>(finding, originalLocations, condition);
                })
                .toList();
    }

    @JsonCreator
    public JoernAnalysisResult(@NotNull @JsonProperty("findings")
                               List<AnnotatedFinding<JoernFinding>> annotatedFindings,
                               @NotNull @JsonProperty("enabledFeatures") Map<String, Boolean> enabledFeatures) {
        super(enabledFeatures);
        this.annotatedFindings = annotatedFindings;
    }

    /**
     * Returns a list of all findings Joern reported.
     *
     * @return a list of all findings
     */
    @Override
    public @NotNull List<AnnotatedFinding<JoernFinding>> getFindings() {
        return annotatedFindings;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JoernAnalysisResult that = (JoernAnalysisResult) o;
        return Objects.equals(annotatedFindings, that.annotatedFindings)
                && Objects.equals(this.getEnabledFeatures(), that.getEnabledFeatures());
    }

    @Override
    public int hashCode() {
        return Objects.hash(annotatedFindings, this.getEnabledFeatures());
    }

    @Override
    public @NotNull String toString() {
        StringBuilder sb = new StringBuilder(super.toString());
        for (AnnotatedFinding<?> finding : this.getFindings()) {
            sb.append(System.lineSeparator());
            sb.append(finding.toString());
        }
        return sb.toString();
    }
}
