package edu.kit.varijoern.composers;

/**
 * Indicates that an error occurred while generating a {@link ConditionTree}.
 */
public class ConditionTreeException extends Exception {
    public ConditionTreeException() {
    }

    public ConditionTreeException(String message) {
        super(message);
    }

    public ConditionTreeException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConditionTreeException(Throwable cause) {
        super(cause);
    }
}
