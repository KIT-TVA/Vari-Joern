package edu.kit.varijoern.featuremodel;

import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.NamedComponentConfigFactory;
import edu.kit.varijoern.featuremodel.featureide.FeatureIDEFMReader;
import edu.kit.varijoern.featuremodel.featureide.FeatureIDEFMReaderConfig;
import edu.kit.varijoern.featuremodel.tortekmax.TorteKmaxFMReader;
import edu.kit.varijoern.featuremodel.tortekmax.TorteKmaxFMReaderConfig;
import org.jetbrains.annotations.NotNull;
import org.tomlj.TomlTable;

import java.nio.file.Path;
import java.util.List;

/**
 * This class is used for parsing the feature model reader section of a configuration file.
 * It uses its {@code name} field to determine which {@link FeatureModelReaderConfig} subclass to use.
 */
public final class FeatureModelReaderConfigFactory extends NamedComponentConfigFactory<FeatureModelReaderConfig> {
    private static final FeatureModelReaderConfigFactory INSTANCE = new FeatureModelReaderConfigFactory();

    private FeatureModelReaderConfigFactory() {
    }

    /**
     * Returns a {@link FeatureModelReaderConfigFactory} instance.
     *
     * @return the instance
     */
    public static @NotNull FeatureModelReaderConfigFactory getInstance() {
        return INSTANCE;
    }

    /**
     * Returns the objects into which the command line arguments for the feature model readers should be parsed. These
     * objects are static. Depending on the configuration, some objects may not be used.
     *
     * @return the objects into which the command line arguments for the feature model readers should be parsed
     */
    public static @NotNull List<Object> getComponentArgs() {
        return List.of(); // Currently, no feature model reader has command line arguments
    }

    @Override
    protected @NotNull FeatureModelReaderConfig newConfigFromName(@NotNull String componentName,
                                                                  @NotNull TomlTable toml, @NotNull Path configPath)
            throws InvalidConfigException {
        return switch (componentName) {
            case FeatureIDEFMReader.NAME -> new FeatureIDEFMReaderConfig(toml, configPath);
            case TorteKmaxFMReader.NAME -> new TorteKmaxFMReaderConfig(toml, configPath);
            default -> throw new InvalidConfigException("Unknown feature model reader \"%s\"".formatted(componentName));
        };
    }

    @Override
    public @NotNull String getComponentType() {
        return "feature model reader";
    }
}
