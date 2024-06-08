package edu.kit.varijoern.analyzers.joern;

import edu.kit.varijoern.analyzers.Evidence;
import edu.kit.varijoern.analyzers.Finding;
import edu.kit.varijoern.composers.PresenceConditionMapper;
import edu.kit.varijoern.composers.sourcemap.SourceMap;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.stream.Collectors;

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
    public JoernFinding(
            @NotNull String name,
            @NotNull String title,
            @NotNull String description,
            double score,
            @NotNull Set<Evidence> evidence) {
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
        return "%s: %s at %s".formatted(this.title,
                this.score,
                this.evidence.stream().map(Evidence::toString).collect(Collectors.joining(", "))
        );
    }

    /**
     * Converts this finding to a string containing information about conditions under which evidence lines are included
     * in the composed code.
     *
     * @param presenceConditionMapper the presence condition mapper to be used
     * @param sourceMap               the source map to be used to determine the location of the evidences in the
     *                                original source
     * @return a string representing this finding
     */
    public @NotNull String toString(@NotNull PresenceConditionMapper presenceConditionMapper,
                                    @NotNull SourceMap sourceMap) {
        return "%s: %s at %s".formatted(this.title,
                this.score,
                this.evidence.stream()
                        .map(evidence -> evidence.toString(presenceConditionMapper, sourceMap))
                        .collect(Collectors.joining(", "))
        );
    }
}
