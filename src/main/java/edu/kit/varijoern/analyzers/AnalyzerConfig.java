package edu.kit.varijoern.analyzers;

import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.NamedComponentConfig;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.nio.file.Path;

public abstract class AnalyzerConfig extends NamedComponentConfig<Analyzer> {
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
     * @param tempPath the directory to use for temporary data
     * @return the new {@link Analyzer}
     */
    public abstract Analyzer newAnalyzer(Path tempPath) throws IOException;
}
