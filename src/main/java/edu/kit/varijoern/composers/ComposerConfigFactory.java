package edu.kit.varijoern.composers;

import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.NamedComponentConfigFactory;
import org.tomlj.TomlTable;

public class ComposerConfigFactory extends NamedComponentConfigFactory<ComposerConfig, Composer> {
    private static final ComposerConfigFactory instance = new ComposerConfigFactory();

    private ComposerConfigFactory() {
    }

    public static ComposerConfigFactory getInstance() {
        return instance;
    }

    @Override
    protected ComposerConfig newConfigFromName(String componentName, TomlTable toml) throws InvalidConfigException {
        return switch (componentName) {
            case "antenna" -> new AntennaComposerConfig(toml);
            default -> throw new InvalidConfigException(String.format("Unknown composer \"%s\"", componentName));
        };
    }

    @Override
    public String getComponentType() {
        return "composer";
    }
}
