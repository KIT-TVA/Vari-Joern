package edu.kit.varijoern.composers;

import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.NamedComponentConfig;
import org.jetbrains.annotations.NotNull;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The base class for all composer configurations.
 */
public abstract class ComposerConfig extends NamedComponentConfig {
    /**
     * Creates a new {@link ComposerConfig} by extracting data from the specified TOML section.
     *
     * @param toml the TOML section
     * @throws InvalidConfigException if the TOML section does not represent a valid analyzer configuration
     */
    protected ComposerConfig(@NotNull TomlTable toml) throws InvalidConfigException {
        super(toml);
    }

    /**
     * Creates a new {@link ComposerConfig} with the specified name.
     *
     * @param name the name of the component
     */
    protected ComposerConfig(@NotNull String name) {
        super(name);
    }

    /**
     * Instantiates a new composer using this configuration.
     *
     * @param tmpPath a {@link Path} to a temporary directory that can be used by the composer. This path must be
     *                absolute.
     * @return the new composer
     * @throws IOException          if an I/O error occurred
     * @throws ComposerException    if the composer failed to initialize
     * @throws InterruptedException if the current thread is interrupted
     */
    public abstract @NotNull Composer newComposer(@NotNull Path tmpPath)
            throws IOException, ComposerException, InterruptedException;

    @Override
    public @NotNull String getComponentType() {
        return "composer";
    }
}
