package edu.kit.varijoern.analyzers.joern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Contains information about a piece of code that caused a finding.
 */
public class JoernEvidence {
    private final String code;
    private final int lineNumber;
    private final int columnNumber;

    /**
     * Creates a new {@link JoernEvidence} containing the specified information.
     *
     * @param code         the source code that caused the finding
     * @param lineNumber   the line number of the location of the evidence
     * @param columnNumber the column number of the location of the evidence
     */
    @JsonCreator
    public JoernEvidence(
        @JsonProperty("code") String code,
        @JsonProperty("lineNumber") int lineNumber,
        @JsonProperty("columnNumber") int columnNumber) {
        this.code = code;
        this.lineNumber = lineNumber;
        this.columnNumber = columnNumber;
    }

    /**
     * Returns the source code that caused the finding.
     *
     * @return the source code
     */
    public String getCode() {
        return code;
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
     * Returns the column number of the location of this evidence.
     *
     * @return the column number
     */
    public int getColumnNumber() {
        return columnNumber;
    }
}
