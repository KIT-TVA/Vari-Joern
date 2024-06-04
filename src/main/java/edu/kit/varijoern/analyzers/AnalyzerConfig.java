package edu.kit.varijoern.analyzers;

import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.NamedComponentConfig;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The base class for all analyzer configurations.
 */
public abstract class AnalyzerConfig extends NamedComponentConfig {
    /**
     * Creates a new {@link AnalyzerConfig} by extracting data from the specified TOML section.
     *
     * @param toml the TOML section
     * @throws InvalidConfigException if the TOML section does not represent a valid analyzer configuration
     */
    protected AnalyzerConfig(TomlTable toml) throws InvalidConfigException {
        super(toml);
    }

    @Override
    public String getComponentType() {
        return "analyzer";
    }

    /**
     * Instantiates a new {@link Analyzer} which uses the specified path for temporary data.
     *
     * @param tempPath the directory to use for temporary data. Must be an absolute path.
     * @return the new {@link Analyzer}
     */
    public abstract Analyzer newAnalyzer(Path tempPath) throws IOException;
}
