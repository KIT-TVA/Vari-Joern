package edu.kit.varijoern;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;
import com.beust.jcommander.converters.PathConverter;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public class Args {
    @Parameter(names = "--verbose", description = "Enable verbose logging")
    private boolean verbose;

    @Parameter(names = "--trace", description = "Enable trace logging")
    private boolean trace;

    @Parameter(names = {"-h", "--help"}, help = true, description = "Show this help message")
    private boolean help;

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
