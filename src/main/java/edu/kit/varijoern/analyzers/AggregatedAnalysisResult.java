package edu.kit.varijoern.analyzers;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Contains all findings found in any variant, grouped by their evidence.
 *
 * @param findingAggregations information about the findings and the variants in which they were found
 */
public record AggregatedAnalysisResult(Set<FindingAggregation> findingAggregations) {
    @Override
    public String toString() {
        return "Aggregated findings:%n%s".formatted(findingAggregations.stream()
                .map(FindingAggregation::toString)
                .collect(Collectors.joining(System.lineSeparator() + System.lineSeparator()))
        );
    }
}
