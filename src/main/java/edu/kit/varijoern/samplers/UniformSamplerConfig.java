package edu.kit.varijoern.samplers;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.TomlUtils;
import org.jetbrains.annotations.NotNull;
import org.tomlj.TomlTable;

public class UniformSamplerConfig extends SamplerConfig {
    private static final String SAMPLE_SIZE_FIELD_NAME = "sample-size";
    private final int sampleSize;

    /**
     * Creates a new {@link SamplerConfig} by extracting data from the specified TOML section.
     *
     * @param toml the TOML section
     * @throws InvalidConfigException if the TOML section does not represent a valid analyzer configuration
     */
    protected UniformSamplerConfig(@NotNull TomlTable toml) throws InvalidConfigException {
        super(toml);
        this.sampleSize = TomlUtils.getMandatoryInt(SAMPLE_SIZE_FIELD_NAME, toml, "Sample size is missing or invalid");
        if (this.sampleSize <= 0) {
            throw new InvalidConfigException("Sample size must be >= 1");
        }
    }

    @Override
    public @NotNull Sampler newSampler(@NotNull IFeatureModel featureModel) {
        return new UniformSampler(featureModel, this.sampleSize);
    }
}
