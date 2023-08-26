package edu.kit.varijoern.composers;

import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.NamedComponentConfig;
import org.tomlj.TomlTable;

public abstract class ComposerConfig extends NamedComponentConfig<Composer> {
    protected ComposerConfig(TomlTable toml) throws InvalidConfigException {
        super(toml);
    }

    public abstract Composer newComposer();

    @Override
    public String getComponentType() {
        return "composer";
    }
}
