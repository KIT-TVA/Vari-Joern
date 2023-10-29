package edu.kit.varijoern.featuremodel;

import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.TomlUtils;
import org.tomlj.TomlTable;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Contains the configuration of the {@link TorteKmaxFMReader}.
 */
public class TorteKmaxFMReaderConfig extends FeatureModelReaderConfig {
    private static final String PATH_FIELD_NAME = "path";
    private final Path sourcePath;

    /**
     * Creates a new {@link TorteKmaxFMReaderConfig} by extracting data from the specified TOML section.
     *
     * @param toml       the TOML section
     * @param configPath the path to the configuration file
     * @throws InvalidConfigException if the TOML section does not represent a valid analyzer configuration
     */
    public TorteKmaxFMReaderConfig(TomlTable toml, Path configPath) throws InvalidConfigException {
        super(toml);
        String path = TomlUtils.getMandatoryString(
            PATH_FIELD_NAME,
            toml,
            "Path to source directory is missing or not a string"
        );
        Path sourcePath;
        try {
            sourcePath = Path.of(path);
        } catch (InvalidPathException e) {
            throw new InvalidConfigException("Path to source directory is not a valid path", e);
        }
        if (!sourcePath.isAbsolute()) {
            System.err.println("Path to source directory is not absolute");
            sourcePath = configPath.getParent().resolve(sourcePath);
            System.err.println("Using " + sourcePath + " instead");
        }
        this.sourcePath = sourcePath;
    }

    @Override
    public FeatureModelReader newFeatureModelReader() {
        return new TorteKmaxFMReader(this.sourcePath);
    }
}
