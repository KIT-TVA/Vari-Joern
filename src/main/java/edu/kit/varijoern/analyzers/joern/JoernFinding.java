package edu.kit.varijoern.analyzers.joern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

/**
 * Contains information about a finding Joern found.
 */
public class JoernFinding {
    private static final String TITLE_KEY = "title";
    private static final String SCORE_KEY = "score";
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

    /**
     * Returns the title of the finding if specified.
     *
     * @return the title of the finding
     */
    public Optional<String> getTitle() {
        return Optional.ofNullable(this.keyValuePairs.get(TITLE_KEY));
    }

    /**
     * Returns the score of this finding if specified.
     *
     * @return the score of this finding
     */
    public Optional<Float> getScore() {
        String scoreString = this.keyValuePairs.get(SCORE_KEY);
        if (scoreString == null)
            return Optional.empty();
        return Optional.of(Float.parseFloat(scoreString));
    }

    @Override
    public String toString() {
        String title = this.getTitle().orElse("(no title)");
        String score = this.getScore().map(String::valueOf).orElse("(no score)");
        StringBuilder sb = new StringBuilder();
        sb.append(title);
        sb.append(": ");
        sb.append(score);
        return sb.toString();
    }
}
