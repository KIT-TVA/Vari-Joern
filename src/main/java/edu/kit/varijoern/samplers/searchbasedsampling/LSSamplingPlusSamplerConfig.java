package edu.kit.varijoern.samplers.searchbasedsampling;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.TomlUtils;
import edu.kit.varijoern.samplers.Sampler;
import edu.kit.varijoern.samplers.SamplerConfig;
import org.jetbrains.annotations.NotNull;
import org.tomlj.TomlInvalidTypeException;
import org.tomlj.TomlTable;

/**
 * Contains the configuration of the LS-Sampling-Plus sampler.
 */
public class LSSamplingPlusSamplerConfig extends SamplerConfig {
    // Literals used in the TOML file for the configuration of the LS-Sampling-Plus sampler.
    private static final String SAMPLE_SIZE_FIELD_NAME = "sample-size";
    private static final String T_FIELD_NAME = "t";
    private static final String LAMBDA_FIELD_NAME = "lambda";
    private static final String DELTA_FIELD_NAME = "delta";
    private static final String DYNAMIC_SAMPLING_PROB_FIELD_NAME = "dynamic-prob";

    // Fields for the configuration of the LS-Sampling-Plus sampler.
    private final int sampleSize;
    private final int t;
    private final int lambda;
    private final int delta;
    private final boolean dynamicSamplingProb;

    /**
     * Creates a new {@link LSSamplingPlusSamplerConfig} by extracting data from the specified TOML section.
     *
     * @param toml the TOML section
     * @throws InvalidConfigException if the TOML section does not represent a valid configuration
     */
    public LSSamplingPlusSamplerConfig(@NotNull TomlTable toml) throws InvalidConfigException {
        super(toml);

        // Target sample size (default: 100).
        long sampleSize;
        try {
            sampleSize = toml.getLong(LSSamplingPlusSamplerConfig.SAMPLE_SIZE_FIELD_NAME, () -> 100L);
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Sample size must be an integer value.", e);
        }
        if (sampleSize <= 0L || sampleSize > Integer.MAX_VALUE) {
            throw new InvalidConfigException(String.format("Sample size must be >= 1 and <= %d.", Integer.MAX_VALUE));
        }
        this.sampleSize = (int) sampleSize;

        // Target feature interaction coverage (default: 2).
        long t;
        try {
            t = toml.getLong(LSSamplingPlusSamplerConfig.T_FIELD_NAME, () -> 2L);
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Parameter t must be an integer value.", e);
        }
        if (t <= 1L || t > Integer.MAX_VALUE) {
            throw new InvalidConfigException(String.format("Parameter t must be >= 2 and <= %d.", Integer.MAX_VALUE));
        }
        this.t = (int) t;

        // Number of candidates per iteration (default: 100).
        long lambda;
        try {
            lambda = toml.getLong(LSSamplingPlusSamplerConfig.LAMBDA_FIELD_NAME, () -> 100L);
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Lambda must be an integer value.", e);
        }
        if (lambda < 0L || lambda > Integer.MAX_VALUE) {
            throw new InvalidConfigException(String.format("Lambda must >= 1 and <= %d.", Integer.MAX_VALUE));
        }
        this.lambda = (int) lambda;

        // Cardinality of the measuring set (default: 1_000_000).
        long delta;
        try {
            delta = toml.getLong(LSSamplingPlusSamplerConfig.DELTA_FIELD_NAME, () -> 1_000_000L);
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Delta must be an integer value.", e);
        }
        if (delta < 0L || delta > Integer.MAX_VALUE) {
            throw new InvalidConfigException(String.format("Delta must be >= 1 and <= %d.", Integer.MAX_VALUE));
        }
        this.delta = (int) delta;

        // Whether sampling probabilities should be updated dynamically (default: true).
        try {
            this.dynamicSamplingProb = toml.getBoolean(LSSamplingPlusSamplerConfig.DYNAMIC_SAMPLING_PROB_FIELD_NAME,
                    () -> true);
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Dynamic sampling probability must be a boolean.", e);
        }
    }

    @Override
    public @NotNull Sampler newSampler(@NotNull IFeatureModel featureModel) {
        return new LSSamplingPlusSampler(featureModel, this.sampleSize, this.t,
                this.lambda, this.delta, this.dynamicSamplingProb);
    }
}


