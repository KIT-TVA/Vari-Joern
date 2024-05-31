package edu.kit.varijoern.analyzers.joern;

import com.beust.jcommander.Parameter;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Contains the command line arguments for the Joern analyzer.
 */
public class JoernArgs {
    @Parameter(names = "--joern-path", description = "Path to the Joern executables")
    @Nullable
    private Path joernPath;

    /**
     * Creates a new {@link JoernArgs} instance with default values.
     */
    public JoernArgs() {
    }

    /**
     * Creates a new {@link JoernArgs} instance with the specified values.
     *
     * @param joernPath the path to the Joern executables
     */
    public JoernArgs(@Nullable Path joernPath) {
        this.joernPath = joernPath;
    }

    /**
     * Returns the path to the Joern executables. If it is {@code null}, the system PATH should be used.
     *
     * @return the path to the Joern executables
     */
    public @Nullable Path getJoernPath() {
        return joernPath;
    }
}
