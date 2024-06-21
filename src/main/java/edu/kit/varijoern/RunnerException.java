package edu.kit.varijoern;

/**
 * Exception thrown by the {@link ParallelIterationRunner} class.
 */
public class RunnerException extends Exception {
    public RunnerException(String message) {
        super(message);
    }

    public RunnerException(String message, Throwable cause) {
        super(message, cause);
    }
}
