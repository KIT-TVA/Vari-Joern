package edu.kit.varijoern;

import com.beust.jcommander.Parameter;

/**
 * Contains information about the output destination and format of the result.
 */
public class ResultOutputArgs {
    @Parameter(names = {"-o", "--output"}, description = "Output file", converter = OutputDestinationConverter.class)
    private OutputDestination destination = new OutputDestination();

    /**
     * Returns the destination for the output.
     *
     * @return the destination for the output
     */
    public OutputDestination getDestination() {
        return destination;
    }
}
