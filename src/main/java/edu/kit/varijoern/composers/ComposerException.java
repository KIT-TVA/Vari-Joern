package edu.kit.varijoern.composers;

/**
 * Indicates that an error occurred while composing.
 */
public class ComposerException extends Exception {
    public ComposerException() {
    }

    public ComposerException(String message) {
        super(message);
    }

    public ComposerException(String message, Throwable cause) {
    }

    public ComposerException(Throwable cause) {
        super(cause);
    }
}
