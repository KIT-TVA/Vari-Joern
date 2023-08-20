package edu.kit.varijoern.analyzers;

import edu.kit.varijoern.config.InvalidConfigException;
import org.tomlj.TomlInvalidTypeException;
import org.tomlj.TomlTable;

import java.nio.file.Path;

public class JoernAnalyzerConfig extends AnalyzerConfig {
    private static final String COMMAND_NAME_FIELD_NAME = "command";
    private static final String JOERN_DEFAULT_COMMAND = "joern-scan";
    private final String commandName;

    public JoernAnalyzerConfig(TomlTable toml) throws InvalidConfigException {
        super(toml);
        try {
            this.commandName = toml.getString(COMMAND_NAME_FIELD_NAME, () -> JOERN_DEFAULT_COMMAND);
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Joern command is not a string");
        }
    }

    @Override
    public Analyzer newAnalyzer(Path workspacePath) {
        return new JoernAnalyzer(this.commandName, workspacePath);
    }

    public String getCommandName() {
        return commandName;
    }
}
