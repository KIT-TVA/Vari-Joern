package edu.kit.varijoern.samplers;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.TomlUtils;
import org.jetbrains.annotations.NotNull;
import org.tomlj.TomlTable;

import java.util.Objects;

public abstract class SamplerConfig {
    private static final String NAME_FIELD_NAME = "name";
    private final String name;

    protected SamplerConfig(TomlTable toml) throws InvalidConfigException {
        this.name = getSamplerName(toml);
    }

    @NotNull
    private static String getSamplerName(TomlTable toml) throws InvalidConfigException {
        return TomlUtils.getMandatoryString(NAME_FIELD_NAME,
            toml,
            "Sampler name is missing or not a string"
        );
    }

    public static SamplerConfig readConfig(TomlTable toml) throws InvalidConfigException {
        String samplerName = getSamplerName(toml);
        return switch (samplerName) {
            case FixedSampler.NAME -> new FixedSamplerConfig(toml);
            default -> throw new InvalidConfigException(String.format("Unknown sampler \"%s\"", samplerName));
        };
    }

    public abstract Sampler newSampler(IFeatureModel featureModel);

    public String getName() {
        return name;
    }
}
