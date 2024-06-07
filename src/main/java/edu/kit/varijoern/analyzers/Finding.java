package edu.kit.varijoern.analyzers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Provides basic information about a finding.
 */
public interface Finding {
    /**
     * Returns a short description of the kind of finding.
     *
     * @return the title of the finding
     */
    @NotNull
    String getTitle();

    /**
     * Returns the evidence that caused this finding.
     *
     * @return the evidence
     */
    @JsonIgnore
    @NotNull
    Set<Evidence> getEvidence();
}
