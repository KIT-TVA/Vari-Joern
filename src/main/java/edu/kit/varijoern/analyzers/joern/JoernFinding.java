package edu.kit.varijoern.analyzers.joern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.kit.varijoern.analyzers.Evidence;
import edu.kit.varijoern.analyzers.Finding;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Contains information about a finding Joern found.
 */
public class JoernFinding implements Finding {
    private final @NotNull String name;
    private final @NotNull String title;
    private final @NotNull String description;
    private final double score;
    private final @NotNull Set<Evidence> evidence;

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
            @NotNull @JsonProperty("name") String name,
            @NotNull @JsonProperty("title") String title,
            @NotNull @JsonProperty("description") String description,
            @JsonProperty("score") double score,
            @NotNull @JsonProperty("evidence") Set<Evidence> evidence) {
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
    @Override
    public @NotNull String getName() {
        return name;
    }

    /**
     * Returns the title of the finding.
     *
     * @return the title of the finding
     */
    public @NotNull String getTitle() {
        return this.title;
    }

    /**
     * Returns a description of the problem.
     *
     * @return a description
     */
    public @NotNull String getDescription() {
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
    @Override
    public @NotNull Set<Evidence> getEvidence() {
        return evidence;
    }

    @Override
    public String toString() {
        return "%s: %s".formatted(this.title, this.score);
    }
}
