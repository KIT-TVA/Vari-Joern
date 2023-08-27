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

    @JsonCreator
    public JoernEvidence(
        @JsonProperty("code") String code,
        @JsonProperty("lineNumber") int lineNumber,
        @JsonProperty("columnNumber") int columnNumber) {
        this.code = code;
        this.lineNumber = lineNumber;
        this.columnNumber = columnNumber;
    }

    public String getCode() {
        return code;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public int getColumnNumber() {
        return columnNumber;
    }
}
