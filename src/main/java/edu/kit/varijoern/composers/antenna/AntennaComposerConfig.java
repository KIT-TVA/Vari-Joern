package edu.kit.varijoern.composers.antenna;

import edu.kit.varijoern.composers.Composer;
import edu.kit.varijoern.composers.ComposerConfig;
import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.SubjectConfig;
import edu.kit.varijoern.config.TomlUtils;
import org.jetbrains.annotations.NotNull;
import org.tomlj.TomlTable;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Contains the configuration of the Antenna composer.
 */
public class AntennaComposerConfig extends ComposerConfig {
    private static final String SOURCE_FIELD_NAME = "source";
    private final @NotNull Path sourceLocation;

    /**
     * Creates a new {@link AntennaComposerConfig} by extracting data from the specified TOML section.
     *
     * @param toml          the TOML section
     * @param subjectConfig the {@link SubjectConfig} with which to resolve the source path if not specified absolute.
     * @throws InvalidConfigException if the TOML section does not represent a valid configuration
     */
    public AntennaComposerConfig(@NotNull TomlTable toml, @NotNull SubjectConfig subjectConfig) throws InvalidConfigException {
        super(toml);
        String sourceLocation = TomlUtils.getMandatoryString(
                SOURCE_FIELD_NAME,
                toml,
                "Source location for Antenna composer is missing or not a string"
        );
        Path sourcePath;
        try {
            sourcePath = Path.of(sourceLocation);
        } catch (InvalidPathException e) {
            throw new InvalidConfigException("Source location for Antenna composer is not a valid path", e);
        }
        if (!sourcePath.isAbsolute()) {
            sourcePath = subjectConfig.getSourceRoot().resolve(sourcePath);
        }
        this.sourceLocation = sourcePath;
    }

    @Override
    public @NotNull Composer newComposer(@NotNull Path tmpPath) {
        return new AntennaComposer(this.sourceLocation);
    }

    /**
     * Returns the location of the source code that will be preprocessed. This is an absolute path.
     *
     * @return the location of the source code
     */
    public @NotNull Path getSourceLocation() {
        return sourceLocation;
    }
}
