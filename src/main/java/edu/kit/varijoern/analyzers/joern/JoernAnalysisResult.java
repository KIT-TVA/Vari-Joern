package edu.kit.varijoern.analyzers.joern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.kit.varijoern.analyzers.AnalysisResult;
import edu.kit.varijoern.samplers.Configuration;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Contains information about the result of running a Joern scan.
 */
public class JoernAnalysisResult extends AnalysisResult<JoernFinding> {
    private final List<JoernFinding> findings;

    /**
     * Creates a new {@link JoernAnalysisResult} from a list of findings.
     *
     * @param findings      the list of findings
     * @param configuration the configuration of the variant this result is for
     */
    @JsonCreator
    public JoernAnalysisResult(@NotNull @JsonProperty("findings") List<JoernFinding> findings,
                               @NotNull @JsonProperty("configuration") Configuration configuration) {
        // Check for null because Jackson does not enforce non-nullability
        super(Objects.requireNonNull(configuration));
        this.findings = Objects.requireNonNull(findings);
    }

    /**
     * Returns a list of all findings Joern reported.
     *
     * @return a list of all findings
     */
    @Override
    public @NotNull List<JoernFinding> getFindings() {
        return findings;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JoernAnalysisResult that = (JoernAnalysisResult) o;
        return Objects.equals(findings, that.findings)
                && Objects.equals(this.getConfiguration(), that.getConfiguration());
    }

    @Override
    public int hashCode() {
        return Objects.hash(findings, this.getConfiguration());
    }

    @Override
    public @NotNull String toString() {
        StringBuilder sb = new StringBuilder(super.toString());
        for (JoernFinding finding : this.getFindings()) {
            sb.append(System.lineSeparator());
            sb.append(finding.toString());
        }
        return sb.toString();
    }
}
