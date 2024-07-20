package edu.kit.varijoern.analyzers;

import edu.kit.varijoern.analyzers.joern.JoernAnalyzer;
import edu.kit.varijoern.analyzers.joern.JoernAnalyzerConfig;
import edu.kit.varijoern.analyzers.joern.JoernArgs;
import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.NamedComponentConfigFactory;
import org.jetbrains.annotations.NotNull;
import org.tomlj.TomlTable;

import java.nio.file.Path;
import java.util.List;

/**
 * This class is used for parsing the analyzer section of a configuration file. It uses its {@code name} field to
 * determine which {@link AnalyzerConfig} subclass to use.
 */
public final class AnalyzerConfigFactory extends NamedComponentConfigFactory<AnalyzerConfig<?>> {
    private static final AnalyzerConfigFactory INSTANCE = new AnalyzerConfigFactory();
    private static final JoernArgs JOERN_ARGS = new JoernArgs();

    private AnalyzerConfigFactory() {
    }

    /**
     * Returns an {@link AnalyzerConfigFactory} instance.
     *
     * @return the instance
     */
    public static @NotNull NamedComponentConfigFactory<AnalyzerConfig<?>> getInstance() {
        return INSTANCE;
    }

    /**
     * Returns the objects into which the command line arguments for the analyzers should be parsed. These objects
     * are static. Depending on the configuration, some objects may not be used.
     *
     * @return the objects into which the command line arguments for the analyzers should be parsed
     */
    public static @NotNull List<Object> getComponentArgs() {
        return List.of(JOERN_ARGS);
    }

    @Override
    protected @NotNull AnalyzerConfig<?> newConfigFromName(@NotNull String componentName, @NotNull TomlTable toml,
                                                        @NotNull Path configPath)
            throws InvalidConfigException {
        return switch (componentName) {
            case JoernAnalyzer.NAME -> new JoernAnalyzerConfig(toml, JOERN_ARGS);
            default -> throw new InvalidConfigException(String.format("Unknown analyzer \"%s\"", componentName));
        };
    }

    @Override
    public @NotNull String getComponentType() {
        return "analyzer";
    }
}
