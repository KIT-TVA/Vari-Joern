package edu.kit.varijoern.composers.antenna;

import edu.kit.varijoern.composers.Composer;
import edu.kit.varijoern.composers.ComposerArgs;
import edu.kit.varijoern.composers.ComposerConfig;
import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.SubjectConfig;
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
    private final ComposerArgs composerArgs;

    /**
     * Creates a new {@link AntennaComposerConfig} by extracting data from the specified TOML section.
     *
     * @param toml          the TOML section
     * @param subjectConfig the {@link SubjectConfig} with which to resolve the source path if not specified absolute.
     * @param composerArgs  the general command line arguments for the composer.
     * @throws InvalidConfigException if the TOML section does not represent a valid configuration
     */
    public AntennaComposerConfig(@NotNull TomlTable toml, @NotNull SubjectConfig subjectConfig,
                                 @NotNull ComposerArgs composerArgs) throws InvalidConfigException {
        super(toml);
        this.composerArgs = composerArgs;
        Path sourcePath;
        try {
            sourcePath = Path.of(toml.getString(SOURCE_FIELD_NAME, () -> "."));
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
        return new AntennaComposer(this.sourceLocation, this.composerArgs.shouldSkipPCs());
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
