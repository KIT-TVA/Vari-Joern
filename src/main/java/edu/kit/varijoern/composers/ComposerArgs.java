package edu.kit.varijoern.composers;

import com.beust.jcommander.Parameter;

/**
 * Contains the general command line arguments for all composers.
 */
public class ComposerArgs {
    @Parameter(names = "--skip-pcs", description = "Skip the presence condition calculation")
    private boolean skipPCs;

    /**
     * Returns whether the presence condition calculation should be skipped.
     *
     * @return {@code true} if the presence condition calculation should be skipped, {@code false} otherwise
     */
    public boolean shouldSkipPCs() {
        return skipPCs;
    }
}
