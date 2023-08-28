package edu.kit.varijoern.analyzers.joern;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contains the data of the `keyValuePairs` section of a Joern finding, e.g. a score.
 */
public class KeyValuePairs {
    private final Map<String, String> pairs;

    /**
     * Creates a new {@link KeyValuePairs} instance containing the specified key-value pairs.
     *
     * @param pairs a list of key-value pairs
     */
    public KeyValuePairs(List<KeyValuePair> pairs) {
        this.pairs = new HashMap<>();
        for (KeyValuePair pair : pairs) {
            this.pairs.put(pair.getKey(), pair.getValue());
        }
    }

    /**
     * Returns value stored for the specified key or {@code null} if no value has been assigned for the key.
     *
     * @param key the key
     * @return the value or {@code null} if no value has been stored
     */
    public String get(String key) {
        return this.pairs.get(key);
    }
}
