package edu.kit.varijoern.analyzers;

import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.NamedComponentConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The base class for all analyzer configurations.
 *
 * @param <T> the type of the analysis results produced by the analyzers created from this configuration
 */
public abstract class AnalyzerConfig<T extends AnalysisResult> extends NamedComponentConfig {
    private final @NotNull ResultAggregator<T> resultAggregator;
    private final @Nullable Path path;

    /**
     * Creates a new {@link AnalyzerConfig} by extracting data from the specified TOML section.
     *
     * @param toml             the TOML section
     * @param resultAggregator the result aggregator to use
     * @throws InvalidConfigException if the TOML section does not represent a valid analyzer configuration
     */
    protected AnalyzerConfig(@NotNull TomlTable toml, @NotNull ResultAggregator<T> resultAggregator)
            throws InvalidConfigException {
        super(toml);

        this.path = Optional.ofNullable(toml.getString("path"))
                .map(Path::of)
                .filter(Files::exists)
                .orElse(null);

        this.resultAggregator = resultAggregator;
    }

    /**
     * Creates a new {@link AnalyzerConfig} with the specified name and result aggregator.
     *
     * @param name             the name of the implementation
     * @param resultAggregator the result aggregator to use
     */
    protected AnalyzerConfig(@NotNull String name, @NotNull ResultAggregator<T> resultAggregator) {
        super(name);
        this.path = null;
        this.resultAggregator = resultAggregator;
    }

    public @Nullable Path getPath() {
        return this.path;
    }

    @Override
    public @NotNull String getComponentType() {
        return "analyzer";
    }

    /**
     * Instantiates a new {@link Analyzer} which uses the specified path for temporary data.
     *
     * @param tempPath the directory to use for temporary data. Must be an absolute path.
     * @return the new {@link Analyzer}
     */
    public abstract @NotNull Analyzer<T> newAnalyzer(@NotNull Path tempPath) throws IOException;

    /**
     * Returns the result aggregate that all analyzers created from this configuration should use. The analyzers should
     * add all results they produce to this aggregator.
     *
     * @return the result aggregator
     */
    public @NotNull ResultAggregator<T> getResultAggregator() {
        return resultAggregator;
    }
}
