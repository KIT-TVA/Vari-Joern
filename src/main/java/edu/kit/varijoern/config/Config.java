package edu.kit.varijoern.config;

import org.tomlj.Toml;
import org.tomlj.TomlInvalidTypeException;
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Collectors;

public class Config {
    private final long iterations;

    /**
     * Parses the configuration file at the specified location. The file format is assumed to be TOML.
     *
     * @param configLocation the path to the configuration file
     * @throws IOException            if an IO error occurs
     * @throws InvalidConfigException if the file does not contain valid TOML
     *                                or does not match the expected configuration format
     */
    public Config(Path configLocation) throws IOException, InvalidConfigException {
        TomlParseResult parsedConfig = Toml.parse(configLocation);
        if (parsedConfig.hasErrors()) {
            String message = parsedConfig.errors().stream()
                .map(TomlParseError::toString)
                .collect(Collectors.joining("\n"));
            throw new InvalidConfigException(message);
        }

        try {
            this.iterations = parsedConfig.getLong("iterations", () -> 1);
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Invalid type of option `iterations`", e);
        }
    }

    /**
     * Indicates how many sampler-composer-analyzer cycles should be executed.
     * @return how many sampler-composer-analyzer cycles should be executed
     */
    public long getIterations() {
        return iterations;
    }
}
