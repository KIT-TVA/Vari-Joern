package edu.kit.varijoern.analyzers;

import com.fasterxml.jackson.annotation.JsonGetter;
import edu.kit.varijoern.composers.sourcemap.SourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.prop4j.Node;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Contains information about a finding, its evidence in the original source code and, if available, its presence
 * condition.
 *
 * @param finding                   the finding
 * @param originalEvidenceLocations the locations in the original source code where the finding was found
 * @param condition                 the presence condition of the finding, if available
 */
public record AnnotatedFinding<T extends Finding>(@NotNull T finding,
                               @JsonGetter("evidence") @NotNull Set<SourceLocation> originalEvidenceLocations,
                               @Nullable Node condition) {
    @Override
    public String toString() {
        return "%s at %s; condition: %s"
                .formatted(this.finding,
                        this.originalEvidenceLocations.stream()
                                .map(SourceLocation::toString)
                                .collect(Collectors.joining(", ")),
                        this.condition == null ? "unknown" : this.condition.toString());
    }
}
