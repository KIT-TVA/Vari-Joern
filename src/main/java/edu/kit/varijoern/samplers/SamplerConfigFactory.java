package edu.kit.varijoern.samplers;

import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.NamedComponentConfigFactory;
import org.tomlj.TomlTable;

import java.nio.file.Path;

/**
 * This class is used for parsing the sampler section of a configuration file. It uses its {@code name} field to
 * determine which {@link SamplerConfig} subclass to use.
 */
public class SamplerConfigFactory extends NamedComponentConfigFactory<SamplerConfig> {
    private static final SamplerConfigFactory instance = new SamplerConfigFactory();

    private SamplerConfigFactory() {
    }

    /**
     * Returns a {@link SamplerConfigFactory} instance.
     *
     * @return the instance
     */
    public static SamplerConfigFactory getInstance() {
        return instance;
    }

    @Override
    protected SamplerConfig newConfigFromName(String componentName, TomlTable toml, Path configPath)
        throws InvalidConfigException {
        return switch (componentName) {
            case FixedSampler.NAME -> new FixedSamplerConfig(toml);
            default -> throw new InvalidConfigException(String.format("Unknown sampler \"%s\"", componentName));
        };
    }

    @Override
    public String getComponentType() {
        return "sampler";
    }
}
