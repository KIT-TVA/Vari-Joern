package edu.kit.varijoern.samplers;

/**
 * Indicates that a sampler encountered an error.
 */
public class SamplerException extends Exception {
    public SamplerException() {
    }

    public SamplerException(String message) {
        super(message);
    }

    public SamplerException(String message, Throwable cause) {
        super(message, cause);
    }

    public SamplerException(Throwable cause) {
        super(cause);
    }
}
