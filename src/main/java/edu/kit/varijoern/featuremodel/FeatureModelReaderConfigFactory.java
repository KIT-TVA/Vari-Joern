package edu.kit.varijoern.featuremodel;

import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.NamedComponentConfigFactory;
import org.tomlj.TomlTable;

import java.nio.file.Path;
import java.util.List;

/**
 * This class is used for parsing the feature model reader section of a configuration file.
 * It uses its {@code name} field to determine which {@link FeatureModelReaderConfig} subclass to use.
 */
public class FeatureModelReaderConfigFactory extends NamedComponentConfigFactory<FeatureModelReaderConfig> {
    private static final FeatureModelReaderConfigFactory instance = new FeatureModelReaderConfigFactory();

    private FeatureModelReaderConfigFactory() {
    }

    /**
     * Returns a {@link FeatureModelReaderConfigFactory} instance.
     *
     * @return the instance
     */
    public static FeatureModelReaderConfigFactory getInstance() {
        return instance;
    }

    /**
     * Returns the objects into which the command line arguments for the feature model readers should be parsed. These
     * objects are static. Depending on the configuration, some objects may not be used.
     *
     * @return the objects into which the command line arguments for the feature model readers should be parsed
     */
    public static List<Object> getComponentArgs() {
        return List.of(); // Currently, no feature model reader has command line arguments
    }

    @Override
    protected FeatureModelReaderConfig newConfigFromName(String componentName, TomlTable toml, Path configPath)
            throws InvalidConfigException {
        return switch (componentName) {
            case FeatureIDEFMReader.NAME -> new FeatureIDEFMReaderConfig(toml, configPath);
            case KconfigReader.NAME -> new KconfigReaderConfig(toml, configPath);
            case TorteKmaxFMReader.NAME -> new TorteKmaxFMReaderConfig(toml, configPath);
            default ->
                    throw new InvalidConfigException(String.format("Unknown feature model reader \"%s\"", componentName));
        };
    }

    @Override
    public String getComponentType() {
        return "feature model reader";
    }
}
