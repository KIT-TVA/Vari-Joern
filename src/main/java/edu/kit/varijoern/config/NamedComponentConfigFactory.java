package edu.kit.varijoern.config;

import org.tomlj.TomlTable;

public abstract class NamedComponentConfigFactory<C extends NamedComponentConfig<T>, T> {
    private static final String NAME_FIELD_NAME = "name";

    public static String getComponentName(TomlTable toml, String componentType) throws InvalidConfigException {
        return TomlUtils.getMandatoryString(
            NAME_FIELD_NAME,
            toml,
            String.format("%s name is missing or not a string", componentType)
        );
    }

    public C readConfig(TomlTable toml) throws InvalidConfigException {
        String componentName = getComponentName(toml, getComponentType());
        return newConfigFromName(componentName, toml);
    }

    protected abstract C newConfigFromName(String componentName, TomlTable toml) throws InvalidConfigException;

    public abstract String getComponentType();
}
