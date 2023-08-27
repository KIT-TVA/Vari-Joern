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

    @JsonCreator
    public JoernFinding(
        @JsonProperty("evidence") List<JoernEvidence> evidence,
        @JsonProperty("keyValuePairs") List<KeyValuePair> keyValuePairs) {
        this.evidence = evidence;
        this.keyValuePairs = new KeyValuePairs(keyValuePairs);
    }

    public List<JoernEvidence> getEvidence() {
        return evidence;
    }

    public KeyValuePairs getKeyValuePairs() {
        return keyValuePairs;
    }
}
