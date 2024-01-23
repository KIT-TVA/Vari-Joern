package edu.kit.varijoern.featuremodel;

import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.TomlUtils;
import org.tomlj.TomlTable;

import java.nio.file.Path;

/**
 * Contains the configuration of the Kconfig reader.
 */
public class KconfigReaderConfig extends FeatureModelReaderConfig {
    private static final String PATH_FIELD_NAME = "path";

    private final Path path;

    /**
     * Creates a new {@link FeatureModelReaderConfig} by extracting data from the specified TOML section.
     *
     * @param toml       the TOML section
     * @param configPath the path to the configuration file
     * @throws InvalidConfigException if the TOML section does not represent a valid analyzer configuration
     */
    protected KconfigReaderConfig(TomlTable toml, Path configPath) throws InvalidConfigException {
        super(toml);
        String pathString = TomlUtils.getMandatoryString(
            PATH_FIELD_NAME,
            toml,
            "Path to directory containing Kconfig file is missing or not a string"
        );
        Path path;
        try {
            path = Path.of(pathString);
        } catch (Exception e) {
            throw new InvalidConfigException("Path to directory containing Kconfig file is not a valid path", e);
        }
        if (!path.isAbsolute()) {
            path = configPath.getParent().resolve(path);
        }
        this.path = path;
    }

    @Override
    public FeatureModelReader newFeatureModelReader() {
        return new KconfigReader(this.path);
    }
}
