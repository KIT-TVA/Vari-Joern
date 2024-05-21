package edu.kit.varijoern.analyzers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.kit.varijoern.composers.FeatureMapper;
import edu.kit.varijoern.composers.sourcemap.SourceLocation;
import edu.kit.varijoern.composers.sourcemap.SourceMap;
import org.prop4j.Node;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Contains information about a piece of code that caused a finding.
 */
public class Evidence {
    private final Optional<SourceLocation> location;

    /**
     * Creates a new {@link Evidence} containing the specified information.
     *
     * @param filename   the name of the file in the composed source in which this evidence was found
     * @param lineNumber the line number of the location of the evidence
     */
    @JsonCreator
    public Evidence(
            @JsonProperty("filename") String filename, @JsonProperty("lineNumber") int lineNumber) {
        this.location = Optional.of(new SourceLocation(Path.of(filename), lineNumber));
    }

    /**
     * Returns the location of the evidence in the composed source.
     *
     * @return the location of the evidence
     */
    public Optional<SourceLocation> getLocation() {
        return location;
    }

    /**
     * Tries to get the condition under which this line is included in the composed source code by using a feature
     * mapper.
     *
     * @param featureMapper the feature mapper to be used
     * @return the condition if it could be determined, an empty {@link Optional} otherwise
     */
    public Optional<Node> getCondition(FeatureMapper featureMapper) {
        return this.location.flatMap(location -> featureMapper.getPresenceCondition(location.file(), location.line()));
    }

    @Override
    public String toString() {
        return this.location.map(SourceLocation::toString).orElse("unknown");
    }

    /**
     * Converts this evidence to a string containing information about when the represented line of code is included in
     * the composed code. To determine the condition, the specified feature mapper is used.
     *
     * @param featureMapper the feature mapper to be used
     * @param sourceMap     the source map to be used to determine the location of this evidence in the original source
     * @return a string representation of this evidence
     */
    public String toString(FeatureMapper featureMapper, SourceMap sourceMap) {
        Optional<Node> condition = getCondition(featureMapper);
        String conditionMessage = condition.map(Node::toString).orElse("unknown");
        return "%s; condition: %s".formatted(
                this.resolveLocation(sourceMap).map(SourceLocation::toString).orElse("unknown"),
                conditionMessage
        );
    }

    /**
     * Resolves the location of this evidence using the specified source map.
     *
     * @param sourceMap the source map to be used
     */
    public Optional<SourceLocation> resolveLocation(SourceMap sourceMap) {
        return this.location.flatMap(sourceMap::getOriginalLocation);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Evidence that = (Evidence) o;
        return Objects.equals(location, that.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(location);
    }
}
