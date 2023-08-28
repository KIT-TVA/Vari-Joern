package edu.kit.varijoern.analyzers.joern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Contains information about a finding Joern found.
 */
public class JoernFinding {
    private final List<JoernEvidence> evidence;
    private final KeyValuePairs keyValuePairs;

    /**
     * Creates a new {@link JoernFinding} containing the specified information.
     *
     * @param evidence      information about the source that caused this finding
     * @param keyValuePairs additional information about this finding
     */
    @JsonCreator
    public JoernFinding(
        @JsonProperty("evidence") List<JoernEvidence> evidence,
        @JsonProperty("keyValuePairs") List<KeyValuePair> keyValuePairs) {
        this.evidence = evidence;
        this.keyValuePairs = new KeyValuePairs(keyValuePairs);
    }

    /**
     * Returns information about the source that caused this finding.
     *
     * @return information about the source that caused this finding
     */
    public List<JoernEvidence> getEvidence() {
        return evidence;
    }

    /**
     * Returns additional data about this finding.
     *
     * @return addition data
     */
    public KeyValuePairs getKeyValuePairs() {
        return keyValuePairs;
    }
}
