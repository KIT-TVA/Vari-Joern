package edu.kit.varijoern.analyzers.joern;

import edu.kit.varijoern.analyzers.Analyzer;
import edu.kit.varijoern.analyzers.AnalyzerConfig;
import edu.kit.varijoern.config.InvalidConfigException;
import org.tomlj.TomlInvalidTypeException;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Contains the configuration of the Joern analyzer.
 */
public class JoernAnalyzerConfig extends AnalyzerConfig {
    private static final String COMMAND_NAME_FIELD_NAME = "command";
    private static final String JOERN_DEFAULT_COMMAND = "joern";
    private final String commandName;

    /**
     * Creates a new {@link JoernAnalyzerConfig} by extracting data from the specified TOML section.
     *
     * @param toml the TOML section
     * @throws InvalidConfigException if the TOML section does not represent a valid configuration
     */
    public JoernAnalyzerConfig(TomlTable toml) throws InvalidConfigException {
        super(toml);
        try {
            this.commandName = toml.getString(COMMAND_NAME_FIELD_NAME, () -> JOERN_DEFAULT_COMMAND);
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Joern command is not a string");
        }
    }

    @Override
    public Analyzer newAnalyzer(Path workspacePath) throws IOException {
        return new JoernAnalyzer(this.commandName, workspacePath);
    }

    /**
     * Returns the command name or path to be used for invoking Joern. The value has either been specified in the
     * configuration file or is a default value.
     *
     * @return the command to be used for invoking Joern
     */
    public String getCommandName() {
        return commandName;
    }
}
