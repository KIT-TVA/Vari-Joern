package edu.kit.varijoern.analyzers;

import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.TomlUtils;
import org.jetbrains.annotations.NotNull;
import org.tomlj.TomlTable;

import java.nio.file.Path;

public abstract class AnalyzerConfig {
    private static final String NAME_FIELD_NAME = "name";
    private final String name;

    protected AnalyzerConfig(TomlTable toml) throws InvalidConfigException {
        this.name = getSamplerName(toml);
    }

    @NotNull
    private static String getSamplerName(TomlTable toml) throws InvalidConfigException {
        return TomlUtils.getMandatoryString(NAME_FIELD_NAME,
            toml,
            "Sampler name is missing or not a string"
        );
    }

    public static AnalyzerConfig readConfig(TomlTable toml) throws InvalidConfigException {
        String samplerName = getSamplerName(toml);
        return switch (samplerName) {
            case JoernAnalyzer.NAME -> new JoernAnalyzerConfig(toml);
            default -> throw new InvalidConfigException(String.format("Unknown sampler \"%s\"", samplerName));
        };
    }

    /**
     * Instantiates a new {@link Analyzer} which uses the specified path for temporary data.
     *
     * @param tempPath the directory to use for temporary data
     * @return the new {@link Analyzer}
     */
    public abstract Analyzer newAnalyzer(Path tempPath);

    public String getName() {
        return name;
    }
}
