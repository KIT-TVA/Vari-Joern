package edu.kit.varijoern.featuremodel;

/**
 * An exception that is thrown when a {@link FeatureModelReader} fails to read a feature model.
 */
public class FeatureModelReaderException extends Exception {
    public FeatureModelReaderException() {
    }

    public FeatureModelReaderException(String message) {
        super(message);
    }

    public FeatureModelReaderException(String message, Throwable cause) {
        super(message, cause);
    }

    public FeatureModelReaderException(Throwable cause) {
        super(cause);
    }
}
