package edu.kit.varijoern.analyzers.joern;

import edu.kit.varijoern.analyzers.Analyzer;
import edu.kit.varijoern.analyzers.AnalyzerConfig;
import edu.kit.varijoern.config.InvalidConfigException;
import org.jetbrains.annotations.Nullable;
import org.tomlj.TomlInvalidTypeException;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Contains the configuration of the Joern analyzer.
 */
public class JoernAnalyzerConfig extends AnalyzerConfig {
    private static final String JOERN_PATH_FIELD_NAME = "joern-path";
    @Nullable
    private final Path joernPath;

    /**
     * Creates a new {@link JoernAnalyzerConfig} by extracting data from the specified TOML section.
     *
     * @param toml       the TOML section
     * @param configPath the path to the configuration file
     * @param args       the command line arguments for the Joern analyzer
     * @throws InvalidConfigException if the TOML section does not represent a valid configuration
     */
    public JoernAnalyzerConfig(TomlTable toml, Path configPath, JoernArgs args) throws InvalidConfigException {
        super(toml);
        this.joernPath = args.getJoernPath();
    }

    @Override
    public Analyzer newAnalyzer(Path workspacePath) throws IOException {
        return new JoernAnalyzer(this.joernPath, workspacePath);
    }

    /**
     * Returns the path to the Joern executables. May be {@code null} to use the system PATH.
     *
     * @return the path to the Joern executables
     */
    @Nullable
    Path getJoernPath() {
        return joernPath;
    }
}
