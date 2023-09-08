package edu.kit.varijoern.analyzers.joern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    @Override
    public String toString() {
        return String.format("%s:%d", this.filename, this.lineNumber);
    }
}
