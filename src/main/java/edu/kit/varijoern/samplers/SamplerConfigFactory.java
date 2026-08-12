package edu.kit.varijoern.samplers;

import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.NamedComponentConfigFactory;
import edu.kit.varijoern.config.SubjectConfig;
import edu.kit.varijoern.samplers.featureinteractionsampling.HSCASampler;
import edu.kit.varijoern.samplers.featureinteractionsampling.HSCASamplerConfig;
import edu.kit.varijoern.samplers.featureinteractionsampling.YASASampler;
import edu.kit.varijoern.samplers.featureinteractionsampling.YASASamplerConfig;
import edu.kit.varijoern.samplers.other.FixedSampler;
import edu.kit.varijoern.samplers.other.FixedSamplerConfig;
import edu.kit.varijoern.samplers.randomsampling.uniform.BddSampler;
import edu.kit.varijoern.samplers.randomsampling.uniform.BddSamplerConfig;
import edu.kit.varijoern.samplers.randomsampling.uniform.SmarchSampler;
import edu.kit.varijoern.samplers.randomsampling.uniform.SmarchSamplerConfig;
import edu.kit.varijoern.samplers.randomsampling.weighted.BaitalSampler;
import edu.kit.varijoern.samplers.randomsampling.weighted.BaitalSamplerConfig;
import edu.kit.varijoern.samplers.searchbasedsampling.LSSamplingPlusSampler;
import edu.kit.varijoern.samplers.searchbasedsampling.LSSamplingPlusSamplerConfig;
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
            // t-wise FIS.
            case YASASampler.NAME -> new YASASamplerConfig(toml);
            case HSCASampler.NAME -> new HSCASamplerConfig(toml);
            // Random sampling.
            case SmarchSampler.NAME -> new SmarchSamplerConfig(toml);
            case BddSampler.NAME -> new BddSamplerConfig(toml);
            case BaitalSampler.NAME -> new BaitalSamplerConfig(toml);
            // Search-based sampling.
            case LSSamplingPlusSampler.NAME -> new LSSamplingPlusSamplerConfig(toml);
            // Other sampling strategies.
            case FixedSampler.NAME -> new FixedSamplerConfig(toml);
            default -> throw new InvalidConfigException(String.format("Unknown sampler \"%s\".", componentName));
        };
    }

    @Override
    public @NotNull String getComponentType() {
        return "sampler";
    }
}
