package edu.kit.varijoern.composers;

import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.TomlUtils;
import org.tomlj.TomlTable;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public class AntennaComposerConfig extends ComposerConfig {
    private static final String SOURCE_FIELD_NAME = "source";
    private final Path sourceLocation;

    public AntennaComposerConfig(TomlTable toml) throws InvalidConfigException {
        super(toml);
        String sourceLocation = TomlUtils.getMandatoryString(
            SOURCE_FIELD_NAME,
            toml,
            "Source location for Antenna composer is missing or not a string"
        );
        try {
            this.sourceLocation = Path.of(sourceLocation);
        } catch (InvalidPathException e) {
            throw new InvalidConfigException("Source location for Antenna composer is not a valid path", e);
        }
    }

    @Override
    public Composer newComposer() {
        return new AntennaComposer(this.sourceLocation);
    }

    public Path getSourceLocation() {
        return sourceLocation;
    }
}
