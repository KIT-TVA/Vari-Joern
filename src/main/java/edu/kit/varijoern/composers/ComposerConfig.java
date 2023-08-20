package edu.kit.varijoern.composers;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.TomlUtils;
import org.jetbrains.annotations.NotNull;
import org.tomlj.TomlTable;

public abstract class ComposerConfig {
    private static final String NAME_FIELD_NAME = "name";
    private final String name;

    protected ComposerConfig(TomlTable toml) throws InvalidConfigException {
        this.name = getComposerName(toml);
    }

    @NotNull
    private static String getComposerName(TomlTable toml) throws InvalidConfigException {
        return TomlUtils.getMandatoryString(NAME_FIELD_NAME,
            toml,
            "Composer name is missing or not a string"
        );
    }

    public static ComposerConfig readConfig(TomlTable toml) throws InvalidConfigException {
        String samplerName = getComposerName(toml);
        return switch (samplerName) {
            case "antenna" -> new AntennaComposerConfig(toml);
            default -> throw new InvalidConfigException(String.format("Unknown composer \"%s\"", samplerName));
        };
    }

    public abstract Composer newComposer();

    public String getName() {
        return name;
    }
}
