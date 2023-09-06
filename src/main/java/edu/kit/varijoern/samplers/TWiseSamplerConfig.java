package edu.kit.varijoern.samplers;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.TomlUtils;
import org.tomlj.TomlInvalidTypeException;
import org.tomlj.TomlTable;

/**
 * Contains the configuration of the t-wise sampler.
 */
public class TWiseSamplerConfig extends SamplerConfig {
    private static final String T_FIELD_NAME = "t";
    private static final String SAMPLE_SIZE_FIELD_NAME = "max-samples";
    private final int t;
    private final int maxSampleSize;

    /**
     * Creates a new {@link TWiseSamplerConfig} by extracting data from the specified TOML section.
     *
     * @param toml the TOML section
     * @throws InvalidConfigException if the TOML section does not represent a valid configuration
     */
    public TWiseSamplerConfig(TomlTable toml) throws InvalidConfigException {
        super(toml);
        this.t = TomlUtils.getMandatoryInt(T_FIELD_NAME, toml, "Parameter t is missing or invalid");
        if (this.t <= 0) {
            throw new InvalidConfigException("Parameter t must be >= 1");
        }
        long maxSampleSize;
        try {
            maxSampleSize = toml.getLong(SAMPLE_SIZE_FIELD_NAME, () -> (long) Integer.MAX_VALUE);
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Sample size must be an integer value", e);
        }
        if (maxSampleSize < 0 || maxSampleSize > Integer.MAX_VALUE) {
            throw new InvalidConfigException("Maximum sample size must be >= 0 and < " + Integer.MAX_VALUE);
        }
        this.maxSampleSize = (int) maxSampleSize;
    }

    @Override
    public Sampler newSampler(IFeatureModel featureModel) {
        return new TWiseSampler(featureModel, this.t, this.maxSampleSize);
    }
}
