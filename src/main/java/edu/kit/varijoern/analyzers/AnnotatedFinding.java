package edu.kit.varijoern.analyzers;

import edu.kit.varijoern.composers.sourcemap.SourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.prop4j.Node;

import java.util.Set;

/**
 * Contains information about a finding, its evidence in the original source code and, if available, its presence
 * condition.
 *
 * @param finding
 * @param originalEvidenceLocations
 * @param condition
 */
public record AnnotatedFinding(@NotNull Finding finding, @NotNull Set<SourceLocation> originalEvidenceLocations,
                               @Nullable Node condition) {
}
