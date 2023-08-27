package edu.kit.varijoern.analyzers.joern;

import edu.kit.varijoern.analyzers.Analyzer;
import edu.kit.varijoern.analyzers.AnalyzerConfig;
import edu.kit.varijoern.config.InvalidConfigException;
import org.tomlj.TomlInvalidTypeException;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.nio.file.Path;

public class JoernAnalyzerConfig extends AnalyzerConfig {
    private static final String COMMAND_NAME_FIELD_NAME = "command";
    private static final String JOERN_DEFAULT_COMMAND = "joern";
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
    public Analyzer newAnalyzer(Path workspacePath) throws IOException {
        return new JoernAnalyzer(this.commandName, workspacePath);
    }

    public String getCommandName() {
        return commandName;
    }
}
