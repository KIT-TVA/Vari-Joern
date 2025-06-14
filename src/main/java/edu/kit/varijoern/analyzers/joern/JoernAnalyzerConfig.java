package edu.kit.varijoern.analyzers.joern;

import edu.kit.varijoern.analyzers.Analyzer;
import edu.kit.varijoern.analyzers.AnalyzerConfig;
import edu.kit.varijoern.config.InvalidConfigException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Contains the configuration of the Joern analyzer.
 */
public class JoernAnalyzerConfig extends AnalyzerConfig<JoernAnalysisResult, JoernFinding> {
    private final @Nullable Path joernPath;

    /**
     * Creates a new {@link JoernAnalyzerConfig} by extracting data from the specified TOML section.
     *
     * @param toml the TOML section
     * @param args the command line arguments for the Joern analyzer
     * @throws InvalidConfigException if the TOML section does not represent a valid configuration
     */
    public JoernAnalyzerConfig(@NotNull TomlTable toml, @NotNull JoernArgs args) throws InvalidConfigException {
        super(toml, new JoernResultAggregator());
        this.joernPath = args.getJoernPath();
    }

    @Override
    public @NotNull Analyzer newAnalyzer(@NotNull Path workspacePath) throws IOException {
        return new JoernAnalyzer(this.joernPath, workspacePath, this.getResultAggregator());
    }

    /**
     * Returns the path to the Joern executables. May be {@code null} to use the system PATH.
     *
     * @return the path to the Joern executables
     */
    public @Nullable Path getJoernPath() {
        return joernPath;
    }
}
