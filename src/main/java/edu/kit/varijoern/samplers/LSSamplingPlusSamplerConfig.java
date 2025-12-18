package edu.kit.varijoern.samplers;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.TomlUtils;
import org.jetbrains.annotations.NotNull;
import org.tomlj.TomlInvalidTypeException;
import org.tomlj.TomlTable;

/**
 * Contains the configuration of the LS-Sampling-Plus sampler.
 */
public class LSSamplingPlusSamplerConfig extends SamplerConfig {
    private static final String SAMPLE_SIZE_FIELD_NAME = "sample-size";
    private static final String T_FIELD_NAME = "t";
    private static final String LAMBDA_FIELD_NAME = "lambda";
    private static final String DELTA_FIELD_NAME = "delta";

    private final int sampleSize;
    private final int t;
    private final int lambda;
    private final int delta;

    /**
     * Creates a new {@link LSSamplingPlusSamplerConfig} by extracting data from the specified TOML section.
     *
     * @param toml the TOML section
     * @throws InvalidConfigException if the TOML section does not represent a valid configuration
     */
    protected LSSamplingPlusSamplerConfig(@NotNull TomlTable toml) throws InvalidConfigException {
        super(toml);
        this.sampleSize = TomlUtils.getMandatoryInt(SAMPLE_SIZE_FIELD_NAME, toml, "Sample size is missing or invalid");
        if (this.sampleSize <= 0) {
            throw new InvalidConfigException("Sample size must be => 1");
        }

        this.t = TomlUtils.getMandatoryInt(T_FIELD_NAME, toml, "Parameter t is missing or invalid");
        if (this.t <= 1) {
            throw new InvalidConfigException("Parameter t must be >= 2");
        }

        long delta;
        try {
            delta = toml.getLong(DELTA_FIELD_NAME, () -> 1000000);
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Delta must be an integer value", e);
        }
        if (delta < 0 || delta > Integer.MAX_VALUE) {
            throw new InvalidConfigException("Delta must be >= 1 and <= " + Integer.MAX_VALUE);
        }
        this.delta = (int) delta;

        long lambda;
        try {
            lambda = toml.getLong(LAMBDA_FIELD_NAME, () -> 100);
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Lambda must be an integer value", e);
        }
        if (lambda < 0 || lambda > Integer.MAX_VALUE) {
            throw new InvalidConfigException("Lambda must >= 1 and <= "  + Integer.MAX_VALUE);
        }
        this.lambda = (int) lambda;
    }

    @Override
    public @NotNull Sampler newSampler(@NotNull IFeatureModel featureModel) {
        return new LSSamplingPlusSampler(featureModel, this.sampleSize, this.t, this.lambda, this.delta);
    }
}


