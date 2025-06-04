package edu.kit.varijoern.featuremodel.tortekmax;

import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.SubjectConfig;
import edu.kit.varijoern.featuremodel.FeatureModelReader;
import edu.kit.varijoern.featuremodel.FeatureModelReaderConfig;
import org.jetbrains.annotations.NotNull;
import org.tomlj.TomlTable;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Contains the configuration of the {@link TorteKmaxFMReader}.
 */
public class TorteKmaxFMReaderConfig extends FeatureModelReaderConfig {
    private static final String PATH_FIELD_NAME = "path";

    private final @NotNull Path sourcePath;
    private final @NotNull String system;

    /**
     * Creates a new {@link TorteKmaxFMReaderConfig} by extracting data from the specified TOML section.
     *
     * @param toml          the TOML section
     * @param subjectConfig the {@link SubjectConfig} with which to resolve the path to the source files of the subject
     *                      systems if not specified absolute.
     * @throws InvalidConfigException if the TOML section does not represent a valid analyzer configuration
     */
    public TorteKmaxFMReaderConfig(@NotNull TomlTable toml, @NotNull SubjectConfig subjectConfig)
            throws InvalidConfigException {
        super(toml);
        Path sourcePath;
        try {
            sourcePath = Path.of(toml.getString(PATH_FIELD_NAME, () -> "."));
        } catch (InvalidPathException e) {
            throw new InvalidConfigException("Path to source directory is not a valid path", e);
        }
        if (!sourcePath.isAbsolute()) {
            sourcePath = subjectConfig.getSourceRoot().resolve(sourcePath);
        }
        this.sourcePath = sourcePath;

        String system = subjectConfig.getSubjectName();
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
