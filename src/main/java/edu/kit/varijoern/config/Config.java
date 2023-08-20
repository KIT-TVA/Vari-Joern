package edu.kit.varijoern.config;

import edu.kit.varijoern.composers.ComposerConfig;
import edu.kit.varijoern.samplers.SamplerConfig;
import org.tomlj.Toml;
import org.tomlj.TomlInvalidTypeException;
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.stream.Collectors;

public class Config {
    private static final String ITERATIONS_FIELD_NAME = "iterations";
    private static final String SAMPLER_FIELD_NAME = "sampler";
    private static final String COMPOSER_FIELD_NAME = "composer";
    private static final String FEATURE_MODEL_FIELD_NAME = "feature-model";

    private final long iterations;
    private final SamplerConfig samplerConfig;
    private final ComposerConfig composerConfig;
    private final Path featureModelPath;

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
            this.iterations = parsedConfig.getLong(ITERATIONS_FIELD_NAME, () -> 1);
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Invalid type of option `iterations`", e);
        }

        if (!parsedConfig.isString(FEATURE_MODEL_FIELD_NAME))
            throw new InvalidConfigException("Feature model path is missing or not a string");
        try {
            this.featureModelPath = Path.of(parsedConfig.getString(FEATURE_MODEL_FIELD_NAME));
        } catch (InvalidPathException e) {
            throw new InvalidConfigException("Feature model path is invalid", e);
        }

        if (!parsedConfig.isTable(SAMPLER_FIELD_NAME))
            throw new InvalidConfigException("`sampler` section is missing");
        this.samplerConfig = SamplerConfig.readConfig(parsedConfig.getTable(SAMPLER_FIELD_NAME));

        if (!parsedConfig.isTable(COMPOSER_FIELD_NAME))
            throw new InvalidConfigException("`composer` section is missing");
        this.composerConfig = ComposerConfig.readConfig(parsedConfig.getTable(COMPOSER_FIELD_NAME));
    }

    /**
     * Indicates how many sampler-composer-analyzer cycles should be executed.
     *
     * @return how many sampler-composer-analyzer cycles should be executed
     */
    public long getIterations() {
        return iterations;
    }

    public SamplerConfig getSamplerConfig() {
        return samplerConfig;
    }

    public ComposerConfig getComposerConfig() {
        return composerConfig;
    }

    public Path getFeatureModelPath() {
        return featureModelPath;
    }
}
