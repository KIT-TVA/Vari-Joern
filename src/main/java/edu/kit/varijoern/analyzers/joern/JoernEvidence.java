package edu.kit.varijoern.analyzers.joern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.kit.varijoern.composers.FeatureMapper;
import org.prop4j.Node;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Contains information about a piece of code that caused a finding.
 */
public class JoernEvidence {
    private final String filename;
    private final int lineNumber;

    /**
     * Creates a new {@link JoernEvidence} containing the specified information.
     *
     * @param filename   the name of the file in which this evidence was found
     * @param lineNumber the line number of the location of the evidence
     */
    @JsonCreator
    public JoernEvidence(
        @JsonProperty("filename") String filename, @JsonProperty("lineNumber") int lineNumber) {
        this.filename = filename;
        this.lineNumber = lineNumber;
    }

    /**
     * Returns the line number of the location of this evidence.
     *
     * @return the line number
     */
    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * Returns the name of the file in which this evidence was found.
     *
     * @return the filename of this evidence
     */
    public String getFilename() {
        return filename;
    }

    /**
     * Tries to get the condition under which this line is included in the composed source code by using a feature
     * mapper.
     *
     * @param featureMapper the feature mapper to be used
     * @return the condition if it could be determined, an empty {@link Optional} otherwise
     */
    public Optional<Node> getCondition(FeatureMapper featureMapper) {
        return featureMapper.getCondition(Path.of(this.filename), this.lineNumber);
    }

    @Override
    public String toString() {
        return String.format("%s:%d", this.filename, this.lineNumber);
    }

    /**
     * Converts this evidence to a string containing information about when the represented line of code is included in
     * the composed code. To determine the condition, the specified feature mapper is used.
     *
     * @param featureMapper the feature mapper to be used
     * @return a string representation of this evidence
     */
    public String toString(FeatureMapper featureMapper) {
        Optional<Node> condition = getCondition(featureMapper);
        String conditionMessage = condition.map(Node::toString).orElse("unknown");
        return "%s; condition: %s".formatted(this.toString(), conditionMessage);
    }
}
