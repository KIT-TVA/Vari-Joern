package edu.kit.varijoern.composers.kbuild;

import edu.kit.varijoern.composers.Composer;
import edu.kit.varijoern.composers.ComposerConfig;
import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.TomlUtils;
import org.tomlj.TomlTable;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Contains the configuration of the Kbuild composer.
 */
public class KbuildComposerConfig extends ComposerConfig {
    private static final String SOURCE_FIELD_NAME = "source";

    private final Path sourceLocation;

    /**
     * Creates a new {@link KbuildComposerConfig} by extracting data from the specified TOML section.
     * Relative paths are assumed to be relative to the specified path of the configuration file.
     *
     * @param toml       the TOML section
     * @param configPath the path to the configuration file
     * @throws InvalidConfigException if the TOML section does not represent a valid configuration
     */
    public KbuildComposerConfig(TomlTable toml, Path configPath) throws InvalidConfigException {
        super(toml);
        String sourceLocation = TomlUtils.getMandatoryString(
            SOURCE_FIELD_NAME,
            toml,
            "Source location for Antenna Kbuild is missing or not a string"
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
    }

    @Override
    public Composer newComposer() {
        return new KbuildComposer(this.sourceLocation);
    }
}
