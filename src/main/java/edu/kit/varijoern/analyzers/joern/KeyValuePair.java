package edu.kit.varijoern.analyzers.joern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A simple class which contains a key and a value. Used to parse Joern findings.
 */
public class KeyValuePair {
    private final String key;
    private final String value;

    /**
     * Creates a new {@link KeyValuePair} with the specified key and value.
     * @param key the key
     * @param value the value
     */
    @JsonCreator
    public KeyValuePair(@JsonProperty("key") String key, @JsonProperty("value") String value) {
        this.key = key;
        this.value = value;
    }

    /**
     * Returns the key of this key-value pair.
     * @return the key
     */
    public String getKey() {
        return key;
    }

    /**
     * Returns the value of this key-value pair.
     * @return the value
     */
    public String getValue() {
        return value;
    }
}
