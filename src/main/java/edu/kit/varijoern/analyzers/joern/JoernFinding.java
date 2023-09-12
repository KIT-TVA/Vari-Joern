package edu.kit.varijoern.analyzers.joern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.kit.varijoern.composers.FeatureMapper;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Contains information about a finding Joern found.
 */
public class JoernFinding {
    private final String name;
    private final String title;
    private final String description;
    private final double score;
    private final List<JoernEvidence> evidence;

    /**
     * Creates a new {@link JoernFinding} containing the specified information.
     *
     * @param name        the name of the query that caused this finding
     * @param title       the title of the finding
     * @param description a description of the problem
     * @param score       the score of the finding
     * @param evidence    information about the source that caused this finding
     */
    @JsonCreator
    public JoernFinding(
        @JsonProperty("name") String name,
        @JsonProperty("title") String title,
        @JsonProperty("description") String description,
        @JsonProperty("score") double score,
        @JsonProperty("evidence") List<JoernEvidence> evidence) {
        this.name = name;
        this.title = title;
        this.description = description;
        this.score = score;
        this.evidence = evidence;
    }

    /**
     * Returns the name of the query that caused this finding.
     *
     * @return the name of the query
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the title of the finding.
     *
     * @return the title of the finding
     */
    public String getTitle() {
        return this.title;
    }

    /**
     * Returns a description of the problem.
     *
     * @return a description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the score of this finding.
     *
     * @return the score of this finding
     */
    public double getScore() {
        return this.score;
    }

    /**
     * Returns information about the source that caused this finding.
     *
     * @return information about the source that caused this finding
     */
    public List<JoernEvidence> getEvidence() {
        return evidence;
    }

    @Override
    public String toString() {
        return "%s: %s at %s".formatted(this.title,
            this.score,
            this.evidence.stream().map(JoernEvidence::toString).collect(Collectors.joining(", "))
        );
    }

    /**
     * Converts this finding to a string containing information about conditions under which evidence lines are included
     * in the composed code.
     *
     * @param featureMapper the feature mapper to be used
     * @return a string representing this finding
     */
    public String toString(FeatureMapper featureMapper) {
        return "%s: %s at %s".formatted(this.title,
            this.score,
            this.evidence.stream()
                .map(joernEvidence -> joernEvidence.toString(featureMapper))
                .collect(Collectors.joining(", "))
        );
    }
}
