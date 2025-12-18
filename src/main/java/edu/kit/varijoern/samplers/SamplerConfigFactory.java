package edu.kit.varijoern.samplers;

import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.NamedComponentConfigFactory;
import edu.kit.varijoern.config.SubjectConfig;
import org.jetbrains.annotations.NotNull;
import org.tomlj.TomlTable;

import java.util.List;

/**
 * This class is used for parsing the sampler section of a configuration file. It uses its {@code name} field to
 * determine which {@link SamplerConfig} subclass to use.
 */
public final class SamplerConfigFactory extends NamedComponentConfigFactory<SamplerConfig> {
    private static final SamplerConfigFactory INSTANCE = new SamplerConfigFactory();

    private SamplerConfigFactory() {
    }

    /**
     * Returns a {@link SamplerConfigFactory} instance.
     *
     * @return the instance
     */
    public static @NotNull SamplerConfigFactory getInstance() {
        return INSTANCE;
    }

    /**
     * Returns the objects into which the command line arguments for the samplers should be parsed. These objects
     * are static. Depending on the configuration, some objects may not be used.
     *
     * @return the objects into which the command line arguments for the samplers should be parsed
     */
    public static @NotNull List<Object> getComponentArgs() {
        return List.of(); // Currently, no sampler has command line arguments
    }

    @Override
    protected @NotNull SamplerConfig newConfigFromName(@NotNull String componentName, @NotNull TomlTable toml,
                                                       @NotNull SubjectConfig subjectConfig)
            throws InvalidConfigException {
        return switch (componentName) {
            case FixedSampler.NAME -> new FixedSamplerConfig(toml);
            case TWiseSampler.NAME -> new TWiseSamplerConfig(toml);
            case UniformSampler.NAME -> new UniformSamplerConfig(toml);
            case BaitalSampler.NAME -> new BaitalSamplerConfig(toml);
            case LSSamplingPlusSampler.NAME -> new LSSamplingPlusSamplerConfig(toml);
            case UniformBddSampler.NAME -> new UniformBddSamplerConfig(toml);
            case HSCASampler.NAME -> new HSCASamplerConfig(toml);
            default -> throw new InvalidConfigException(String.format("Unknown sampler \"%s\"", componentName));
        };
    }

    @Override
    public @NotNull String getComponentType() {
        return "sampler";
    }
}
