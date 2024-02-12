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
    protected ComposerConfig(TomlTable toml) throws InvalidConfigException {
        super(toml);
    }

    /**
     * Instantiates a new composer using this configuration.
     *
     * @param tmpPath a {@link Path} to a temporary directory that can be used by the composer
     * @return the new composer
     */
    public abstract Composer newComposer(@NotNull Path tmpPath) throws IOException;

    @Override
    public String getComponentType() {
        return "composer";
    }
}
