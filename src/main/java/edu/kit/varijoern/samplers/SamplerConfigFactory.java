package edu.kit.varijoern.samplers;

import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.NamedComponentConfigFactory;
import org.tomlj.TomlTable;

import java.nio.file.Path;

public class SamplerConfigFactory extends NamedComponentConfigFactory<SamplerConfig, Sampler> {
    private static final SamplerConfigFactory instance = new SamplerConfigFactory();

    private SamplerConfigFactory() {
    }

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
