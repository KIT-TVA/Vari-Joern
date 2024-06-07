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
    protected ComposerConfig(@NotNull TomlTable toml) throws InvalidConfigException {
        super(toml);
    }

    /**
     * Instantiates a new composer using this configuration.
     *
     * @param tmpPath a {@link Path} to a temporary directory that can be used by the composer. This path must be
     *                absolute.
     * @return the new composer
     */
    public abstract @NotNull Composer newComposer(@NotNull Path tmpPath) throws IOException, ComposerException;

    @Override
    public @NotNull String getComponentType() {
        return "composer";
    }
}
