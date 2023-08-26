package edu.kit.varijoern.config;

import org.tomlj.TomlTable;

public abstract class NamedComponentConfig<T> {
    private final String name;

    protected NamedComponentConfig(TomlTable toml) throws InvalidConfigException {
        this.name = NamedComponentConfigFactory.getComponentName(toml, getComponentType());
    }

    public abstract String getComponentType();

    public String getName() {
        return this.name;
    }
}
