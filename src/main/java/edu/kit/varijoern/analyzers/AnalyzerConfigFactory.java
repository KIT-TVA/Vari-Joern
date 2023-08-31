package edu.kit.varijoern.analyzers;

import edu.kit.varijoern.analyzers.joern.JoernAnalyzer;
import edu.kit.varijoern.analyzers.joern.JoernAnalyzerConfig;
import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.NamedComponentConfigFactory;
import org.tomlj.TomlTable;

import java.nio.file.Path;

/**
 * This class is used for parsing the analyzer section of a configuration file. It uses its {@code name} field to
 * determine which {@link AnalyzerConfig} subclass to use.
 */
public class AnalyzerConfigFactory extends NamedComponentConfigFactory<AnalyzerConfig> {
    private static final AnalyzerConfigFactory instance = new AnalyzerConfigFactory();

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

    @Override
    protected AnalyzerConfig newConfigFromName(String componentName, TomlTable toml, Path configPath)
        throws InvalidConfigException {
        return switch (componentName) {
            case JoernAnalyzer.NAME -> new JoernAnalyzerConfig(toml);
            default -> throw new InvalidConfigException(String.format("Unknown analyzer \"%s\"", componentName));
        };
    }

    @Override
    public String getComponentType() {
        return "analyzer";
    }
}
