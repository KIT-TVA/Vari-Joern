package edu.kit.varijoern.cli;

import com.beust.jcommander.Parameter;
import edu.kit.varijoern.output.JSONOutputFormatter;
import edu.kit.varijoern.output.OutputFormatter;
import org.jetbrains.annotations.NotNull;

/**
 * Contains information about the output destination and format of the result.
 */
public class ResultOutputArgs {
    @Parameter(names = {"-o", "--output"},
            description = "Where to write the output. Accepts a file name or \"-\", which prints to stdout.",
            converter = OutputDestinationConverter.class)
    private @NotNull OutputDestination destination = new OutputDestination();

    @Parameter(names = {"-f", "--format"}, description = "Output format, allowed values: json (default)",
            converter = OutputFormatterConverter.class)
    private @NotNull OutputFormatter formatter = new JSONOutputFormatter();

    /**
     * Returns the destination for the output.
     *
     * @return the destination for the output
     */
    public @NotNull OutputDestination getDestination() {
        return destination;
    }

    /**
     * Returns the formatter for the output.
     *
     * @return the formatter for the output
     */
    public @NotNull OutputFormatter getFormatter() {
        return formatter;
    }
}
