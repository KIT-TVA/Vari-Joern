package edu.kit.varijoern.cli;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;
import com.beust.jcommander.converters.PathConverter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public class Args {
    @Parameter(names = {"-s", "--strategy"}, description = "The analysis strategy (product or family)", required = true,
            validateWith = StrategyParameterValidator.class, converter = AnalysisStrategyConverter.class)
    private @NotNull AnalysisStrategy strategy;

    @Parameter(names = "--verbose", description = "Enable verbose logging")
    private boolean verbose;

    @Parameter(names = "--trace", description = "Enable trace logging")
    private boolean trace;

    @Parameter(names = {"-h", "--help"}, help = true, description = "Show this help message")
    private boolean help;

    // Parameters specific to the product-based strategy.
    @Parameter(names = "--composers", description = "Number of composer threads to use (product-based)",
            validateWith = PositiveIntegerValidator.class)
    private int numComposers = 1;

    @Parameter(names = "--analyzers", description = "Number of analyzer threads to use (product-based)",
            validateWith = PositiveIntegerValidator.class)
    private int numAnalyzers = 1;

    @Parameter(names = "--composition-queue",
            description = "Maximum number of compositions to queue up for analysis (product-based)",
            validateWith = PositiveIntegerValidator.class)
    private int compositionQueueCapacity = 1;

    @Parameter(names = "--sequential",
            description = "Run the analysis sequentially (product-based). "
                    + "No composer will run in parallel to an analyzer.")
    private boolean sequential = false;

    // Parameters specific to the family-based strategy.
    @Parameter(names = "--sugarlyzer-workers", description = "The number of concurrent workers to use for desugaring "
            + "/analysis within Sugarlyzer (family-based)", validateWith = PositiveIntegerValidator.class)
    private int sugarlyzerWorkers = 1;

    @Parameter(names = "--sugarlyzer-max-heap", description = "The number of gigabytes to use as the maximum heap size "
            + "for every Sugarlyzer worker (family-based)", validateWith = PositiveIntegerValidator.class)
    private int sugarlyzerWorkerMaxHeap = 8;

    @Parameter(names = "--result-cache", description = "Use the specified directory as a cache for results",
            converter = PathConverter.class)
    private @Nullable Path resultCache;

    @Parameter(description = "<path to configuration file>", required = true, converter = PathConverter.class)
    private @NotNull Path config;

    @ParametersDelegate
    private @NotNull ResultOutputArgs resultOutputArgs = new ResultOutputArgs();

    public @NotNull AnalysisStrategy getAnalysisStrategy() {
        return this.strategy;
    }

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
     * Returns whether the analysis should be run sequentially.
     *
     * @return true if the analysis should be run sequentially
     */
    public boolean isSequential() {
        return sequential;
    }

    /**
     * Returns the number concurrent workers to use inside Sugarlyzer.
     *
     * @return the number of workers to use.
     */
    public int getSugarlyzerWorkers() {
        return sugarlyzerWorkers;
    }

    /**
     * Returns the maximum size in gigabytes a worker inside Sugarlyzer should use for its heap.
     *
     * @return the maximum heap size in gigabytes.
     */
    public int getSugarlyzerWorkerMaxHeap() {
        return sugarlyzerWorkerMaxHeap;
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
