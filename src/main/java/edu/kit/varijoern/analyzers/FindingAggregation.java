package edu.kit.varijoern.analyzers;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.kit.varijoern.composers.sourcemap.SourceLocation;
import org.prop4j.Node;

import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Contains information about a finding and the variants in which it was found. Findings are aggregated into one finding
 * and stored in an instance of this class if their evidence refers to the same source code locations.
 */
public class FindingAggregation {
    private final Finding finding;
    private final Set<Map<String, Boolean>> affectedAnalyzedVariants;
    private final Set<Node> possibleConditions;
    private final Set<SourceLocation> originalEvidenceLocations;

    /**
     * Creates a new {@link FindingAggregation} containing the specified information.
     *
     * @param finding                   the finding
     * @param affectedAnalyzedVariants  the variants in which the finding was found
     * @param possibleConditions        the presence conditions determined using the feature mappers for the affected
     *                                  analyzed variants
     * @param originalEvidenceLocations the original source locations of the evidence that caused the finding
     */
    public FindingAggregation(Finding finding, Set<Map<String, Boolean>> affectedAnalyzedVariants,
                              Set<Node> possibleConditions, Set<SourceLocation> originalEvidenceLocations) {
        this.finding = finding;
        this.affectedAnalyzedVariants = affectedAnalyzedVariants;
        this.possibleConditions = Set.copyOf(possibleConditions);
        this.originalEvidenceLocations = originalEvidenceLocations;
    }

    /**
     * Returns the finding.
     *
     * @return the finding
     */
    public Finding getFinding() {
        return finding;
    }

    /**
     * Returns the variants in which the finding was found.
     *
     * @return the affected analyzed variants
     */
    public Set<Map<String, Boolean>> getAffectedAnalyzedVariants() {
        return affectedAnalyzedVariants;
    }

    /**
     * Returns the presence conditions determined using the feature mappers for the affected analyzed variants. If a
     * condition could not be determined using a feature mapper, it is not included in the result.
     *
     * @return the presence conditions
     */
    public Set<Node> getPossibleConditions() {
        return possibleConditions;
    }

    /**
     * Returns the original source locations of the evidence that caused the finding.
     *
     * @return the original evidence locations
     */
    @JsonProperty("evidence")
    public Set<SourceLocation> getOriginalEvidenceLocations() {
        return originalEvidenceLocations;
    }

    @Override
    public String toString() {
        return "Finding: %s%nAnalyzed affected variants: %s%nPossible conditions: %s"
                .formatted(finding, affectedAnalyzedVariants, getPossibleConditions());
    }
}
