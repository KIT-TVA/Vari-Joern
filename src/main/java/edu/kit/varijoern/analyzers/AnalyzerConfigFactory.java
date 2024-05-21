package edu.kit.varijoern.analyzers;

import edu.kit.varijoern.analyzers.joern.JoernAnalyzer;
import edu.kit.varijoern.analyzers.joern.JoernAnalyzerConfig;
import edu.kit.varijoern.analyzers.joern.JoernArgs;
import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.NamedComponentConfigFactory;
import org.tomlj.TomlTable;

import java.nio.file.Path;
import java.util.List;

/**
 * This class is used for parsing the analyzer section of a configuration file. It uses its {@code name} field to
 * determine which {@link AnalyzerConfig} subclass to use.
 */
public class AnalyzerConfigFactory extends NamedComponentConfigFactory<AnalyzerConfig> {
    private static final AnalyzerConfigFactory instance = new AnalyzerConfigFactory();
    private static final JoernArgs joernArgs = new JoernArgs();

    private AnalyzerConfigFactory() {
    }

    /**
     * Returns an {@link AnalyzerConfigFactory} instance.
     *
     * @return the instance
     */
    public static NamedComponentConfigFactory<AnalyzerConfig> getInstance() {
        return instance;
    }

    /**
     * Returns the objects into which the command line arguments for the analyzers should be parsed. These objects
     * are static. Depending on the configuration, some objects may not be used.
     *
     * @return the objects into which the command line arguments for the analyzers should be parsed
     */
    public static List<Object> getComponentArgs() {
        return List.of(joernArgs);
    }

    @Override
    protected AnalyzerConfig newConfigFromName(String componentName, TomlTable toml, Path configPath)
            throws InvalidConfigException {
        return switch (componentName) {
            case JoernAnalyzer.NAME -> new JoernAnalyzerConfig(toml, configPath, joernArgs);
            default -> throw new InvalidConfigException(String.format("Unknown analyzer \"%s\"", componentName));
        };
    }

    @Override
    public String getComponentType() {
        return "analyzer";
    }
}
