package edu.kit.varijoern.analyzers;

import edu.kit.varijoern.composers.FeatureMapper;
import org.prop4j.Node;

import java.util.Optional;
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
    String getTitle();

    /**
     * Returns the evidence that caused this finding.
     *
     * @return the evidence
     */
    Set<Evidence> getEvidence();

    /**
     * Determines the condition for the finding using a {@link FeatureMapper}. The default implementation returns the
     * condition of the first evidence if there is only one evidence. Otherwise, it returns an empty optional.
     *
     * @param featureMapper the feature mapper to use
     * @return the condition of the finding, if it can be determined
     */
    default Optional<Node> getCondition(FeatureMapper featureMapper) {
        Set<Evidence> evidence = this.getEvidence();
        if (evidence.size() != 1)
            return Optional.empty();
        return evidence.stream().findFirst().flatMap(e -> e.getCondition(featureMapper));
    }
}
