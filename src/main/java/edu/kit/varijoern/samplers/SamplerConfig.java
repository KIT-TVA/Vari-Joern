package edu.kit.varijoern.samplers;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.NamedComponentConfig;
import org.tomlj.TomlTable;

public abstract class SamplerConfig extends NamedComponentConfig<Sampler> {
    protected SamplerConfig(TomlTable toml) throws InvalidConfigException {
        super(toml);
    }

    public abstract Sampler newSampler(IFeatureModel featureModel);

    @Override
    public String getComponentType() {
        return "sampler";
    }
}
