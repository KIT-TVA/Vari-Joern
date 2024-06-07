package edu.kit.varijoern.samplers;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.NamedComponentConfig;
import org.jetbrains.annotations.NotNull;
import org.tomlj.TomlTable;

/**
 * The base class for all sampler configurations.
 */
public abstract class SamplerConfig extends NamedComponentConfig {
    /**
     * Creates a new {@link SamplerConfig} by extracting data from the specified TOML section.
     *
     * @param toml the TOML section
     * @throws InvalidConfigException if the TOML section does not represent a valid analyzer configuration
     */
    protected SamplerConfig(@NotNull TomlTable toml) throws InvalidConfigException {
        super(toml);
    }

    /**
     * Instantiates a new {@link Sampler} which uses the specified path for temporary data.
     *
     * @param featureModel the feature model of the source code to generate samples for
     * @return the new {@link Sampler}
     */
    public abstract @NotNull Sampler newSampler(@NotNull IFeatureModel featureModel);

    @Override
    public @NotNull String getComponentType() {
        return "sampler";
    }
}
