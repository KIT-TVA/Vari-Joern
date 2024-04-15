package edu.kit.varijoern;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.converters.PathConverter;

import java.nio.file.Path;

public class Args {
    @Parameter(names = "--verbose", description = "Enable verbose logging")
    private boolean verbose;

    @Parameter(names = "--trace", description = "Enable trace logging")
    private boolean trace;

    @Parameter(names = {"-h", "--help"}, help = true, description = "Show this help message")
    private boolean help;

    @Parameter(description = "<path to configuration file>", required = true, converter = PathConverter.class)
    private Path config;

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
    public Path getConfig() {
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
}
