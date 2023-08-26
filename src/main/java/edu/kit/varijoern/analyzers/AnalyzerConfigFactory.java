package edu.kit.varijoern.analyzers;

import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.NamedComponentConfigFactory;
import org.tomlj.TomlTable;

public class AnalyzerConfigFactory extends NamedComponentConfigFactory<AnalyzerConfig, Analyzer> {
    private static final AnalyzerConfigFactory instance = new AnalyzerConfigFactory();

    private AnalyzerConfigFactory() {
    }

    public static NamedComponentConfigFactory<AnalyzerConfig, Analyzer> getInstance() {
        return instance;
    }

    @Override
    protected AnalyzerConfig newConfigFromName(String componentName, TomlTable toml) throws InvalidConfigException {
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
