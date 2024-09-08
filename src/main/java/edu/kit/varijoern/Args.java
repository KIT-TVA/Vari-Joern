package edu.kit.varijoern;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;
import com.beust.jcommander.converters.PathConverter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public class Args {
    @Parameter(names = "--verbose", description = "Enable verbose logging")
    private boolean verbose;

    @Parameter(names = "--trace", description = "Enable trace logging")
    private boolean trace;

    @Parameter(names = {"-h", "--help"}, help = true, description = "Show this help message")
    private boolean help;

    @Parameter(names = "--composers", description = "Number of composer threads to use",
            validateWith = PositiveIntegerValidator.class)
    private int numComposers = 1;

    @Parameter(names = "--analyzers", description = "Number of analyzer threads to use",
            validateWith = PositiveIntegerValidator.class)
    private int numAnalyzers = 1;

    @Parameter(names = "--composition-queue", description = "Maximum number of compositions to queue up for analysis",
            validateWith = PositiveIntegerValidator.class)
    private int compositionQueueCapacity = 1;

    @Parameter(names = "--result-cache", description = "Use the specified directory as a cache for results",
            converter = PathConverter.class)
    private @Nullable Path resultCache;

    @Parameter(description = "<path to configuration file>", required = true, converter = PathConverter.class)
    private @NotNull Path config;

    @ParametersDelegate
    private @NotNull ResultOutputArgs resultOutputArgs = new ResultOutputArgs();

    /**
     * Returns whether verbose logging is enabled. This corresponds to logging level DEBUG.
     *
     * @return true if verbose logging is enabled
     */
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Returns whether trace logging is enabled. This corresponds to logging level TRACE.
     *
     * @return true if trace logging is enabled
     */
    public boolean isTrace() {
        return trace;
    }

    /**
     * Returns the number of composer threads to use.
     *
     * @return the number of composer threads to use
     */
    public int getNumComposers() {
        return numComposers;
    }

    /**
     * Returns the number of analyzer threads to use.
     *
     * @return the number of analyzer threads to use
     */
    public int getNumAnalyzers() {
        return numAnalyzers;
    }

    /**
     * Returns the size of the composition queue.
     *
     * @return the size of the composition queue
     */
    public int getCompositionQueueCapacity() {
        return compositionQueueCapacity;
    }

    /**
     * Returns the path to the result cache directory or null if no cache is used.
     *
     * @return the path to the result cache directory or null
     */
    public @Nullable Path getResultCache() {
        return resultCache;
    }

    /**
     * Returns the path to the configuration file.
     *
     * @return the path to the configuration file
     */
    public @NotNull Path getConfig() {
        return config;
    }

    /**
     * Returns whether the help message should be shown.
     *
     * @return true if the help message should be shown
     */
    public boolean isHelp() {
        return help;
    }

    /**
     * Returns information about the format of the output and its destination.
     *
     * @return information about the format of the output and its destination
     */
    public @NotNull ResultOutputArgs getResultOutputArgs() {
        return resultOutputArgs;
    }
}
