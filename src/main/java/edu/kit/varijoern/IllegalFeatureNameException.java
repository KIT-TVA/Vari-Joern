package edu.kit.varijoern;

import antenna.preprocessor.v3.PPException;

/**
 * Indicates that a feature name is not supported.
 */
public class IllegalFeatureNameException extends Exception {
    public IllegalFeatureNameException() {
    }

    public IllegalFeatureNameException(String message) {
        super(message);
    }

    public IllegalFeatureNameException(String message, Throwable cause) {
        super(message, cause);
    }

    public IllegalFeatureNameException(Throwable cause) {
        super(cause);
    }

    public IllegalFeatureNameException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
