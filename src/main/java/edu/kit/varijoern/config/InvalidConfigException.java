package edu.kit.varijoern.config;

public class InvalidConfigException extends Exception {
    public InvalidConfigException() {
    }

    public InvalidConfigException(String message) {
        super(message);
    }

    public InvalidConfigException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidConfigException(Throwable cause) {
        super(cause);
    }
}
