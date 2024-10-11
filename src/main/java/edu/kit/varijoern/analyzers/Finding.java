package edu.kit.varijoern.analyzers;

import edu.kit.varijoern.composers.sourcemap.SourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.prop4j.Node;

import java.util.Set;

/**
 * Provides basic information about a finding.
 */
public interface Finding {
    /**
     * Returns the name of the kind of this finding.
     *
     * @return the name
     */
    @NotNull
    String getName();

    /**
     * Returns the set of source locations that caused this finding. The paths are relative to root of the original
     * source code.
     *
     * @return the set of source locations
     */
    @NotNull
    Set<SourceLocation> getEvidence();

    /**
     * Returns the presence condition of this finding.
     *
     * @return the presence condition of this finding
     */
    @Nullable Node getCondition();
}
