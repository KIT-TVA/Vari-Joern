package edu.kit.varijoern.composers;

import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.NamedComponentConfig;
import org.tomlj.TomlTable;

/**
 * The base class for all composer configurations.
 */
public abstract class ComposerConfig extends NamedComponentConfig<Composer> {
    protected ComposerConfig(TomlTable toml) throws InvalidConfigException {
        super(toml);
    }

    /**
     * Instantiates a new composer using this configuration.
     *
     * @return the new composer
     */
    public abstract Composer newComposer();

    @Override
    public String getComponentType() {
        return "composer";
    }
}
