package edu.kit.varijoern.config;

import org.tomlj.TomlTable;

import java.nio.file.Path;

/**
 * This is the base class for classes which are used to parse a component section of the configuration file.
 *
 * @param <C> the type of the object containing the parsed configuration data
 */
public abstract class NamedComponentConfigFactory<C extends NamedComponentConfig> {
    private static final String NAME_FIELD_NAME = "name";

    /**
     * Extracts the name of the implementation of a component from the specified TOML section.
     *
     * @param toml          the TOML section
     * @param componentType the name of the type of component. Used for exception messages.
     * @return the name of the implementation
     * @throws InvalidConfigException if the name could not be extracted from the TOML section
     */
    public static String getComponentName(TomlTable toml, String componentType) throws InvalidConfigException {
        return TomlUtils.getMandatoryString(
            NAME_FIELD_NAME,
            toml,
            String.format("%s name is missing or not a string", componentType)
        );
    }

    /**
     * Creates a new configuration instance by parsing the specified component section of the configuration file.
     * Relative paths are resolved against the parent directory of the specified configuration file path.
     *
     * @param toml       the component section
     * @param configPath the path to the configuration file
     * @return the parsed configuration
     * @throws InvalidConfigException if the TOML section did not represent a valid configuration
     */
    public C readConfig(TomlTable toml, Path configPath) throws InvalidConfigException {
        String componentName = getComponentName(toml, getComponentType());
        return newConfigFromName(componentName, toml, configPath);
    }

    /**
     * Creates a new configuration instance for the component implementation with the specified name by parsing the
     * specified component section of the configuration file. Relative paths are resolved against the parent directory
     * of the specified configuration file path.
     *
     * @param componentName the name of the implementation of the component
     * @param toml          the component section
     * @param configPath    the path to the configuration file
     * @return the parsed configuration
     * @throws InvalidConfigException if the TOML section did not represent a valid configuration
     */
    protected abstract C newConfigFromName(String componentName, TomlTable toml, Path configPath)
        throws InvalidConfigException;

    /**
     * Returns the name of the type of the component this class parses configurations for. The name is used for
     * exception messages.
     *
     * @return the type of the component
     */
    public abstract String getComponentType();
}
