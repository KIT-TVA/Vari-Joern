package edu.kit.varijoern.featuremodel.featureide;

import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.SubjectConfig;
import edu.kit.varijoern.config.TomlUtils;
import edu.kit.varijoern.featuremodel.FeatureModelReader;
import edu.kit.varijoern.featuremodel.FeatureModelReaderConfig;
import org.jetbrains.annotations.NotNull;
import org.tomlj.TomlTable;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Contains the configuration of the FeatureIDE feature model reader.
 */
public class FeatureIDEFMReaderConfig extends FeatureModelReaderConfig {
    private static final String PATH_FIELD_NAME = "path";

    private final @NotNull Path featureModelPath;

    /**
     * Creates a new {@link FeatureIDEFMReaderConfig} by extracting data from the specified TOML section.
     *
     * @param toml          the TOML section
     * @param subjectConfig the {@link SubjectConfig} with which to resolve the path to the feature model if not
     *                      specified absolute.
     * @throws InvalidConfigException if the TOML section does not represent a valid configuration
     */
    public FeatureIDEFMReaderConfig(@NotNull TomlTable toml, @NotNull SubjectConfig subjectConfig)
            throws InvalidConfigException {
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
            featureModelPath = subjectConfig.getSourceRoot().resolve(featureModelPath);
        }
        this.featureModelPath = featureModelPath;
    }

    @Override
    public @NotNull FeatureModelReader newFeatureModelReader() {
        return new FeatureIDEFMReader(this.featureModelPath);
    }

    /**
     * Returns the absolute path to the FeatureIDE feature model.
     *
     * @return the absolute path to the FeatureIDE feature model
     */
    public @NotNull Path getFeatureModelPath() {
        return this.featureModelPath;
    }
}
