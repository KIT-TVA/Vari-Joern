package edu.kit.varijoern.samplers.randomsampling.weighted;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.samplers.Sampler;
import edu.kit.varijoern.samplers.SamplerConfig;
import org.jetbrains.annotations.NotNull;
import org.tomlj.TomlInvalidTypeException;
import org.tomlj.TomlTable;

/**
 * Contains the configuration of the Baital sampler.
 */
public class BaitalSamplerConfig extends SamplerConfig {
    // Literals used in the TOML file for the configuration of the Baital sampler.
    private static final String SAMPLE_SIZE_FIELD_NAME = "sample-size";
    private static final String T_FIELD_NAME = "t";
    private static final String STRATEGY_FIELD_NAME = "strategy";
    private static final String ROUNDS_FIELD_NAME = "rounds";
    private static final String EXTRA_SAMPLES_FIELD_NAME = "extra-samples";

    // Fields for the configuration of the Baital sampler.
    private final int sampleSize;
    private final int t;
    private final int strategy;
    private final int rounds;
    private final boolean extraSamples;

    /**
     * Creates a new {@link BaitalSamplerConfig} by extracting data from the specified TOML section.
     *
     * @param toml the TOML section.
     * @throws InvalidConfigException if the TOML section does not represent a valid configuration.
     */
    public BaitalSamplerConfig(@NotNull TomlTable toml) throws InvalidConfigException {
        super(toml);

        // Target sample size (default: 500).
        long sampleSize;
        try {
            sampleSize = toml.getLong(BaitalSamplerConfig.SAMPLE_SIZE_FIELD_NAME, () -> 500L);
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Sample size must be an integer value.", e);
        }
        if (sampleSize <= 0L || sampleSize > Integer.MAX_VALUE) {
            throw new InvalidConfigException(String.format("Sample size must be >= 1 and < %d.", Integer.MAX_VALUE));
        }
        this.sampleSize = (int) sampleSize;

        // Target feature interaction coverage (default: 2).
        long t;
        try {
            t = toml.getLong(BaitalSamplerConfig.T_FIELD_NAME, () -> 2L);
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Parameter t must be an integer value.", e);
        }
        if (t <= 0L || t > Integer.MAX_VALUE) {
            throw new InvalidConfigException(String.format("Parameter t must be >= 1 and < %d.", Integer.MAX_VALUE));
        }
        this.t = (int) t;

        // Weight generation strategy (default: 5).
        long strategy;
        try {
            strategy = toml.getLong(BaitalSamplerConfig.STRATEGY_FIELD_NAME, () -> 5L);
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Strategy must be an integer value.", e);
        }
        if (strategy < 1L || strategy > 5L) {
            throw new InvalidConfigException("Strategy must be >= 1 and <= 5.");
        }
        this.strategy = (int) strategy;

        // Rounds of sample generation (default: 10).
        long rounds;
        try {
            rounds = toml.getLong(BaitalSamplerConfig.ROUNDS_FIELD_NAME, () -> 10L);
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Rounds must be an integer value.", e);
        }
        if (rounds < 1L || rounds > Integer.MAX_VALUE) {
            throw new InvalidConfigException(String.format("Rounds must be >= 1 and < %d.", Integer.MAX_VALUE));
        }
        this.rounds = (int) rounds;

        // Whether extra samples for optimization should be generated (default: false).
        try {
            this.extraSamples = toml.getBoolean(BaitalSamplerConfig.EXTRA_SAMPLES_FIELD_NAME, () -> false);
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Extra samples toggle must be a boolean.", e);
        }
    }

    @Override
    public @NotNull Sampler newSampler(@NotNull IFeatureModel featureModel) {
        return new BaitalSampler(featureModel, this.sampleSize, this.t, this.strategy, this.rounds, this.extraSamples);
    }
}
