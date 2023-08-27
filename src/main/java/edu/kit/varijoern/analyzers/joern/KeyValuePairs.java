package edu.kit.varijoern.analyzers.joern;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contains the data of the `keyValuePairs` section of a Joern finding, e.g. a score.
 */
public class KeyValuePairs {
    private final Map<String, String> pairs;

    public KeyValuePairs(List<KeyValuePair> pairs) {
        this.pairs = new HashMap<>();
        for (KeyValuePair pair : pairs) {
            this.pairs.put(pair.getKey(), pair.getValue());
        }
    }

    public String get(String key) {
        return this.pairs.get(key);
    }
}
