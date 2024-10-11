package edu.kit.varijoern.composers;

import edu.kit.varijoern.composers.antenna.AntennaComposer;
import edu.kit.varijoern.composers.antenna.AntennaComposerConfig;
import edu.kit.varijoern.composers.kbuild.KbuildComposer;
import edu.kit.varijoern.composers.kbuild.KbuildComposerConfig;
import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.NamedComponentConfigFactory;
import edu.kit.varijoern.config.SubjectConfig;
import org.jetbrains.annotations.NotNull;
import org.tomlj.TomlTable;

import java.util.List;

/**
 * This class is used for parsing the composer section of a configuration file. It uses its {@code name} field to
 * determine which {@link ComposerConfig} subclass to use.
 */
public final class ComposerConfigFactory extends NamedComponentConfigFactory<ComposerConfig> {
    private static final ComposerConfigFactory INSTANCE = new ComposerConfigFactory();
    private static final ComposerArgs COMPOSER_ARGS = new ComposerArgs();

    private ComposerConfigFactory() {
    }

    /**
     * Returns a {@link ComposerConfigFactory} instance.
     *
     * @return the instance
     */
    public static @NotNull ComposerConfigFactory getInstance() {
        return INSTANCE;
    }

    /**
     * Returns the objects into which the command line arguments for the composers should be parsed. These objects
     * are static. Depending on the configuration, some objects may not be used.
     *
     * @return the objects into which the command line arguments for the composers should be parsed
     */
    public static @NotNull List<Object> getComponentArgs() {
        return List.of(COMPOSER_ARGS);
    }

    @Override
    protected @NotNull ComposerConfig newConfigFromName(@NotNull String componentName, @NotNull TomlTable toml,
                                                        @NotNull SubjectConfig subjectConfig)
            throws InvalidConfigException {
        return switch (componentName) {
            case AntennaComposer.NAME -> new AntennaComposerConfig(toml, subjectConfig, COMPOSER_ARGS);
            case KbuildComposer.NAME -> new KbuildComposerConfig(toml, subjectConfig, COMPOSER_ARGS);
            default -> throw new InvalidConfigException(String.format("Unknown composer \"%s\"", componentName));
        };
    }

    @Override
    public @NotNull String getComponentType() {
        return "composer";
    }
}
