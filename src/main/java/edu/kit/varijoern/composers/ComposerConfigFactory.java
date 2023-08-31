package edu.kit.varijoern.composers;

import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.NamedComponentConfigFactory;
import org.tomlj.TomlTable;

import java.nio.file.Path;

/**
 * This class is used for parsing the composer section of a configuration file. It uses its {@code name} field to
 * determine which {@link ComposerConfig} subclass to use.
 */
public class ComposerConfigFactory extends NamedComponentConfigFactory<ComposerConfig> {
    private static final ComposerConfigFactory instance = new ComposerConfigFactory();

    private ComposerConfigFactory() {
    }

    /**
     * Returns a {@link ComposerConfigFactory} instance.
     *
     * @return the instance
     */
    public static ComposerConfigFactory getInstance() {
        return instance;
    }

    @Override
    protected ComposerConfig newConfigFromName(String componentName, TomlTable toml, Path configPath)
        throws InvalidConfigException {
        return switch (componentName) {
            case "antenna" -> new AntennaComposerConfig(toml, configPath);
            default -> throw new InvalidConfigException(String.format("Unknown composer \"%s\"", componentName));
        };
    }

    @Override
    public String getComponentType() {
        return "composer";
    }
}
