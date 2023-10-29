package edu.kit.varijoern.featuremodel;

import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.TomlUtils;
import org.tomlj.TomlTable;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

/**
 * Contains the configuration of the FeatureIDE feature model reader.
 */
public class FeatureIDEFMReaderConfig extends FeatureModelReaderConfig {
    private static final String PATH_FIELD_NAME = "path";

    private final Path featureModelPath;

    /**
     * Creates a new {@link FeatureIDEFMReaderConfig} by extracting data from the specified TOML section.
     *
     * @param toml       the TOML section
     * @param configPath the path to the configuration file
     * @throws InvalidConfigException if the TOML section does not represent a valid configuration
     */
    public FeatureIDEFMReaderConfig(TomlTable toml, Path configPath) throws InvalidConfigException {
        super(toml);
        String path = TomlUtils.getMandatoryString(
            PATH_FIELD_NAME,
            toml,
            "Path to FeatureIDE feature model is missing or not a string"
        );
        Path featureModelPath;
        try {
            featureModelPath = Path.of(path);
        } catch (InvalidPathException e) {
            throw new InvalidConfigException("Path to FeatureIDE feature model is not a valid path", e);
        }
        if (!featureModelPath.isAbsolute()) {
            featureModelPath = configPath.getParent().resolve(featureModelPath);
        }
        this.featureModelPath = featureModelPath;
    }

    @Override
    public FeatureModelReader newFeatureModelReader() {
        return new FeatureIDEFMReader(this.featureModelPath);
    }

    /**
     * Returns the path to the FeatureIDE feature model.
     *
     * @return the path to the FeatureIDE feature model
     */
    public Path getFeatureModelPath() {
        return this.featureModelPath;
    }
}
