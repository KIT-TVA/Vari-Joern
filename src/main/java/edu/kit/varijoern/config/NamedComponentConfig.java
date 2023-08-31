package edu.kit.varijoern.config;

import org.tomlj.TomlTable;

/**
 * This is the base class for all configurations of components, such as analyzers and composers. In the configuration
 * file, the implementations of these components are identified by the {@code name} field in the component's section.
 */
public abstract class NamedComponentConfig {
    private final String name;

    /**
     * Creates a new {@link NamedComponentConfig} by extracting data from the specified TOML section.
     *
     * @param toml the TOML section
     * @throws InvalidConfigException if the TOML section does not represent a valid analyzer configuration
     */
    protected NamedComponentConfig(TomlTable toml) throws InvalidConfigException {
        this.name = NamedComponentConfigFactory.getComponentName(toml, getComponentType());
    }

    /**
     * Returns the name of the type of the component this class configures. This is used for error messages.
     *
     * @return the type of the component
     */
    public abstract String getComponentType();

    /**
     * Returns the name of the implementation chosen for the component.
     *
     * @return the name of the implementation
     */
    public String getName() {
        return this.name;
    }
}
