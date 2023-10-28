package edu.kit.varijoern.config;

import edu.kit.varijoern.analyzers.AnalyzerConfig;
import edu.kit.varijoern.analyzers.AnalyzerConfigFactory;
import edu.kit.varijoern.composers.ComposerConfig;
import edu.kit.varijoern.composers.ComposerConfigFactory;
import edu.kit.varijoern.featuremodel.FeatureModelReaderConfig;
import edu.kit.varijoern.featuremodel.FeatureModelReaderConfigFactory;
import edu.kit.varijoern.samplers.SamplerConfig;
import edu.kit.varijoern.samplers.SamplerConfigFactory;
import org.tomlj.Toml;
import org.tomlj.TomlInvalidTypeException;
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.stream.Collectors;

/**
 * This class contains the complete configuration of Vari-Joern. This includes the subsections for components.
 */
public class Config {
    private static final String FEATURE_MODEL_READER_FIELD_NAME = "feature-model-reader";
    private static final String ITERATIONS_FIELD_NAME = "iterations";
    private static final String SAMPLER_FIELD_NAME = "sampler";
    private static final String COMPOSER_FIELD_NAME = "composer";
    private static final String ERR_SECTION_MISSING_FMT = "`%s` section is missing";
    private static final String ANALYZER_FIELD_NAME = "analyzer";

    private final long iterations;
    private final FeatureModelReaderConfig featureModelReaderConfig;
    private final SamplerConfig samplerConfig;
    private final ComposerConfig composerConfig;
    private final AnalyzerConfig analyzerConfig;

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

        if (!parsedConfig.isTable(SAMPLER_FIELD_NAME))
            throw new InvalidConfigException(String.format(ERR_SECTION_MISSING_FMT, SAMPLER_FIELD_NAME));
        this.samplerConfig = SamplerConfigFactory.getInstance()
            .readConfig(parsedConfig.getTable(SAMPLER_FIELD_NAME), configLocation);

        if (!parsedConfig.isTable(COMPOSER_FIELD_NAME))
            throw new InvalidConfigException(String.format(ERR_SECTION_MISSING_FMT, COMPOSER_FIELD_NAME));
        this.composerConfig = ComposerConfigFactory.getInstance()
            .readConfig(parsedConfig.getTable(COMPOSER_FIELD_NAME), configLocation);

        if (!parsedConfig.isTable(ANALYZER_FIELD_NAME))
            throw new InvalidConfigException(String.format(ERR_SECTION_MISSING_FMT, ANALYZER_FIELD_NAME));
        this.analyzerConfig = AnalyzerConfigFactory.getInstance()
            .readConfig(parsedConfig.getTable(ANALYZER_FIELD_NAME), configLocation);

        if (!parsedConfig.isTable(FEATURE_MODEL_READER_FIELD_NAME))
            throw new InvalidConfigException(String.format(ERR_SECTION_MISSING_FMT, FEATURE_MODEL_READER_FIELD_NAME));
        this.featureModelReaderConfig = FeatureModelReaderConfigFactory.getInstance()
            .readConfig(parsedConfig.getTable(FEATURE_MODEL_READER_FIELD_NAME), configLocation);
    }

    /**
     * Indicates how many sampler-composer-analyzer cycles should be executed.
     *
     * @return how many sampler-composer-analyzer cycles should be executed
     */
    public long getIterations() {
        return iterations;
    }

    /**
     * Returns the configuration of the feature model reader component.
     *
     * @return the feature model reader configuration
     */
    public FeatureModelReaderConfig getFeatureModelReaderConfig() {
        return featureModelReaderConfig;
    }

    /**
     * Returns the configuration of the sampler component.
     *
     * @return the sampler configuration
     */
    public SamplerConfig getSamplerConfig() {
        return samplerConfig;
    }

    /**
     * Returns the configuration of the composer component.
     *
     * @return the composer configuration
     */
    public ComposerConfig getComposerConfig() {
        return composerConfig;
    }

    /**
     * Returns the configuration of the analyzer component.
     *
     * @return the analyzer configuration
     */
    public AnalyzerConfig getAnalyzerConfig() {
        return analyzerConfig;
    }
}
