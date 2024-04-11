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
     * @param toml the TOML section
     * @throws InvalidConfigException if the TOML section does not represent a valid configuration
     */
    public JoernAnalyzerConfig(TomlTable toml, Path configPath) throws InvalidConfigException {
        super(toml);
        try {
            String rawJoernPath = toml.getString(JOERN_PATH_FIELD_NAME, () -> "");
            Path joernPath = rawJoernPath.isEmpty() ? null : Path.of(rawJoernPath);
            if (joernPath != null && !joernPath.isAbsolute()) {
                joernPath = configPath.getParent().resolve(joernPath);
            }
            this.joernPath = joernPath;
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Joern command is not a string");
        }
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
