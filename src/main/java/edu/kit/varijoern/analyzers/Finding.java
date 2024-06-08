package edu.kit.varijoern.analyzers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jetbrains.annotations.NotNull;

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
     * Returns the evidence that caused this finding.
     *
     * @return the evidence
     */
    @JsonIgnore
    @NotNull
    Set<Evidence> getEvidence();
}
