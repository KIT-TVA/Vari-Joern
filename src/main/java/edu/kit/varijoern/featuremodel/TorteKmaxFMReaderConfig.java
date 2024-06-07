package edu.kit.varijoern.featuremodel;

import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.TomlUtils;
import org.jetbrains.annotations.NotNull;
import org.tomlj.TomlTable;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Contains the configuration of the {@link TorteKmaxFMReader}.
 */
public class TorteKmaxFMReaderConfig extends FeatureModelReaderConfig {
    private static final String PATH_FIELD_NAME = "path";
    private static final String SYSTEM_FIELD_NAME = "system";

    private final @NotNull Path sourcePath;
    private final @NotNull String system;

    /**
     * Creates a new {@link TorteKmaxFMReaderConfig} by extracting data from the specified TOML section.
     *
     * @param toml       the TOML section
     * @param configPath the path to the configuration file. Must be absolute.
     * @throws InvalidConfigException if the TOML section does not represent a valid analyzer configuration
     */
    public TorteKmaxFMReaderConfig(@NotNull TomlTable toml, @NotNull Path configPath) throws InvalidConfigException {
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
            sourcePath = configPath.getParent().resolve(sourcePath);
        }
        this.sourcePath = sourcePath;

        String system = TomlUtils.getMandatoryString(
                SYSTEM_FIELD_NAME,
                toml,
                "System is missing or not a string"
        );
        if (!TorteKmaxFMReader.supportsSystem(system)) {
            throw new InvalidConfigException("Unknown system: " + system);
        }
        this.system = system;
    }

    @Override
    public @NotNull FeatureModelReader newFeatureModelReader() {
        return new TorteKmaxFMReader(this.sourcePath, this.system);
    }
}
