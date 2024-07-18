package edu.kit.varijoern.config;

import edu.kit.varijoern.analyzers.AnalyzerConfig;
import edu.kit.varijoern.analyzers.AnalyzerConfigFactory;
import edu.kit.varijoern.cli.AnalysisStrategy;
import edu.kit.varijoern.composers.ComposerConfig;
import edu.kit.varijoern.composers.ComposerConfigFactory;
import edu.kit.varijoern.featuremodel.FeatureModelReaderConfig;
import edu.kit.varijoern.featuremodel.FeatureModelReaderConfigFactory;
import edu.kit.varijoern.samplers.SamplerConfig;
import edu.kit.varijoern.samplers.SamplerConfigFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tomlj.Toml;
import org.tomlj.TomlInvalidTypeException;
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This class contains the complete configuration of Vari-Joern. This includes the subsections for components.
 */
public class Config {
    private static final String ITERATIONS_FIELD_NAME = "iterations";
    private static final String ANALYZER_FIELD_NAME = "analyzer";
    private static final String PROGRAM_FIELD_NAME = "program";
    private static final String FEATURE_MODEL_READER_FIELD_NAME = "feature-model-reader";
    private static final String PRODUCT_FIELD_NAME = "product";
    private static final String SAMPLER_FIELD_NAME = "sampler";
    private static final String COMPOSER_FIELD_NAME = "composer";
    private static final String ERR_SECTION_MISSING_FMT = "`%s` section is missing";

    // General config.
    private final long iterations;
    private final @NotNull AnalyzerConfig analyzerConfig;
    private final @NotNull ProgramConfig programConfig;
    private final @NotNull FeatureModelReaderConfig featureModelReaderConfig;

    // Product-based config.
    private @Nullable SamplerConfig samplerConfig;
    private @Nullable ComposerConfig composerConfig;

    /**
     * Parses the configuration file at the specified location. The file format is assumed to be TOML.
     *
     * @param configLocation   the path to the configuration file. May be relative to the current working directory.
     * @param analysisStrategy the analysis strategy chosen by the user.
     * @throws IOException            if an IO error occurs
     * @throws InvalidConfigException if the file does not contain valid TOML
     *                                or does not match the expected configuration format
     */
    public Config(@NotNull Path configLocation, @NotNull AnalysisStrategy analysisStrategy) throws IOException, InvalidConfigException {
        Path absoluteConfigLocation = configLocation.isAbsolute()
                ? configLocation
                : Path.of(System.getProperty("user.dir")).resolve(configLocation);

        TomlParseResult parsedConfig = Toml.parse(absoluteConfigLocation);
        if (parsedConfig.hasErrors()) {
            String message = parsedConfig.errors().stream()
                    .map(TomlParseError::toString)
                    .collect(Collectors.joining("\n"));
            throw new InvalidConfigException(message);
        }

        // Collect general configuration.
        try {
            this.iterations = parsedConfig.getLong(ITERATIONS_FIELD_NAME, () -> 1);
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Invalid type of option `iterations`", e);
        }

        if (!parsedConfig.isTable(ANALYZER_FIELD_NAME))
            throw new InvalidConfigException(String.format(ERR_SECTION_MISSING_FMT, ANALYZER_FIELD_NAME));
        this.analyzerConfig = AnalyzerConfigFactory.getInstance()
                .readConfig(Objects.requireNonNull(parsedConfig.getTable(ANALYZER_FIELD_NAME)), absoluteConfigLocation);

        if (!parsedConfig.isTable(PROGRAM_FIELD_NAME))
            throw new InvalidConfigException(String.format(ERR_SECTION_MISSING_FMT, PROGRAM_FIELD_NAME));
        this.programConfig = new ProgramConfig(Objects.requireNonNull(parsedConfig.getTable(PROGRAM_FIELD_NAME)));

        if (!parsedConfig.isTable(FEATURE_MODEL_READER_FIELD_NAME))
            throw new InvalidConfigException(String.format(ERR_SECTION_MISSING_FMT, FEATURE_MODEL_READER_FIELD_NAME));
        this.featureModelReaderConfig = FeatureModelReaderConfigFactory.getInstance()
                .readConfig(Objects.requireNonNull(parsedConfig.getTable(FEATURE_MODEL_READER_FIELD_NAME)),
                        absoluteConfigLocation);

        if (analysisStrategy == AnalysisStrategy.PRODUCT) {
            // Collect product specific configuration.
            if (!parsedConfig.isTable(PRODUCT_FIELD_NAME))
                throw new InvalidConfigException(String.format(ERR_SECTION_MISSING_FMT, PRODUCT_FIELD_NAME));

            var productTable = Objects.requireNonNull(parsedConfig.getTable(PRODUCT_FIELD_NAME));

            if (!productTable.isTable(SAMPLER_FIELD_NAME))
                throw new InvalidConfigException(String.format(ERR_SECTION_MISSING_FMT, SAMPLER_FIELD_NAME));
            this.samplerConfig = SamplerConfigFactory.getInstance()
                    .readConfig(Objects.requireNonNull(productTable.getTable(SAMPLER_FIELD_NAME)), absoluteConfigLocation);

            if (!productTable.isTable(COMPOSER_FIELD_NAME))
                throw new InvalidConfigException(String.format(ERR_SECTION_MISSING_FMT, COMPOSER_FIELD_NAME));
            this.composerConfig = ComposerConfigFactory.getInstance()
                    .readConfig(Objects.requireNonNull(productTable.getTable(COMPOSER_FIELD_NAME)), absoluteConfigLocation);
        }
    }

    /**
     * Indicates how many sampler-composer-analyzer cycles should be executed.
     *
     * @return how many sampler-composer-analyzer cycles should be executed
     */
    public long getIterations() {
        return iterations;
    }

    public @NotNull ProgramConfig getProgramConfig() {
        return this.programConfig;
    }

    /**
     * Returns the configuration of the feature model reader component.
     *
     * @return the feature model reader configuration
     */
    public @NotNull FeatureModelReaderConfig getFeatureModelReaderConfig() {
        return featureModelReaderConfig;
    }

    /**
     * Returns the configuration of the sampler component.
     *
     * @return the sampler configuration
     */
    public @Nullable SamplerConfig getSamplerConfig() {
        return samplerConfig;
    }

    /**
     * Returns the configuration of the composer component.
     *
     * @return the composer configuration
     */
    public @Nullable ComposerConfig getComposerConfig() {
        return composerConfig;
    }

    /**
     * Returns the configuration of the analyzer component.
     *
     * @return the analyzer configuration
     */
    public @NotNull AnalyzerConfig getAnalyzerConfig() {
        return analyzerConfig;
    }
}
