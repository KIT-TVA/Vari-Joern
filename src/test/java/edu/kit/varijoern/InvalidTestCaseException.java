package edu.kit.varijoern;

/**
 * An exception indicating that a test case does not conform to the expected format or does not exist.
 */
public class InvalidTestCaseException extends RuntimeException {
    public InvalidTestCaseException(String message) {
        super(message);
    }

    public InvalidTestCaseException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidTestCaseException(Throwable cause) {
        super(cause);
    }

    public InvalidTestCaseException() {
        super();
    }
}
