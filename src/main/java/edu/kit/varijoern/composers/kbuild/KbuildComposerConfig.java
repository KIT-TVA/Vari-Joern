package edu.kit.varijoern.composers.kbuild;

import edu.kit.varijoern.composers.Composer;
import edu.kit.varijoern.composers.ComposerConfig;
import edu.kit.varijoern.composers.ComposerException;
import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.TomlUtils;
import org.jetbrains.annotations.NotNull;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Contains the configuration of the Kbuild composer.
 */
public class KbuildComposerConfig extends ComposerConfig {
    private static final String SOURCE_FIELD_NAME = "source";
    private static final String SYSTEM_FIELD_NAME = "system";

    private final Path sourceLocation;
    private final String system;

    /**
     * Creates a new {@link KbuildComposerConfig} by extracting data from the specified TOML section.
     * Relative paths are assumed to be relative to the specified path of the configuration file.
     *
     * @param toml       the TOML section
     * @param configPath the path to the configuration file. Must be absolute.
     * @throws InvalidConfigException if the TOML section does not represent a valid configuration
     */
    public KbuildComposerConfig(TomlTable toml, Path configPath) throws InvalidConfigException {
        super(toml);
        String sourceLocation = TomlUtils.getMandatoryString(
                SOURCE_FIELD_NAME,
                toml,
                "Source location for Kbuild composer is missing or not a string"
        );
        Path sourcePath;
        try {
            sourcePath = Path.of(sourceLocation);
        } catch (InvalidPathException e) {
            throw new InvalidConfigException("Source location for Kbuild composer is not a valid path", e);
        }
        if (!sourcePath.isAbsolute()) {
            sourcePath = configPath.getParent().resolve(sourcePath);
        }
        this.sourceLocation = sourcePath;

        this.system = TomlUtils.getMandatoryString(
                SYSTEM_FIELD_NAME,
                toml,
                "System for Kbuild composer is missing or not a string"
        );
        if (!KbuildComposer.isSupportedSystem(this.system)) {
            throw new InvalidConfigException("System for Kbuild composer is not supported");
        }
    }

    @Override
    public Composer newComposer(@NotNull Path tmpPath) throws IOException, ComposerException {
        return new KbuildComposer(this.sourceLocation, this.system, tmpPath);
    }
}
