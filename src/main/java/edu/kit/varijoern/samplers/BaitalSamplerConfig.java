package edu.kit.varijoern.samplers;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.TomlUtils;
import org.jetbrains.annotations.NotNull;
import org.tomlj.TomlInvalidTypeException;
import org.tomlj.TomlTable;

/**
 * Contains the configuration of the baital sampler.
 */
public class BaitalSamplerConfig extends SamplerConfig {
    private static final String SAMPLE_SIZE_FIELD_NAME = "sample-size";
    private static final String T_FIELD_NAME = "t";
    private static final String STRATEGY_FIELD_NAME = "strategy";
    private static final String ROUNDS_FIELD_NAME = "rounds";
    private static final String EXTRA_SAMPLES_FIELD_NAME = "extra-samples";

    private final int sampleSize;
    private final int t;
    private final int strategy;
    private final int rounds;
    private final boolean extraSamples;

    /**
     * Creates a new {@link BaitalSamplerConfig} by extracting data from the specified TOML section.
     *
     * @param toml the TOML section
     * @throws InvalidConfigException if the TOML section does not represent a valid configuration
     */
    protected BaitalSamplerConfig(@NotNull TomlTable toml) throws InvalidConfigException {
        super(toml);
        this.sampleSize = TomlUtils.getMandatoryInt(SAMPLE_SIZE_FIELD_NAME, toml, "Sample size is missing or invalid");
        if (this.sampleSize <= 0) {
            throw new InvalidConfigException("Sample size must be >= 1");
        }

        this.t = TomlUtils.getMandatoryInt(T_FIELD_NAME, toml, "Parameter t is missing or invalid");
        if (this.t <= 0) {
            throw new InvalidConfigException("Parameter t must be >= 1");
        }

        long strategy;
        try {
            strategy = toml.getLong(STRATEGY_FIELD_NAME, () -> 5);
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Strategy must be an integer value" + e);
        }
        if (strategy < 1 || strategy > 5) {
            throw new InvalidConfigException("Strategy must be >= 1 and <= 5");
        }
        this.strategy = (int) strategy;

        long rounds;
        try {
            rounds = toml.getLong(ROUNDS_FIELD_NAME, () -> 10);
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Rounds must be an integer value" + e);
        }
        if (rounds < 1) {
            throw new InvalidConfigException("Rounds must be >= 1");
        }
        this.rounds = (int) rounds;

        try {
            this.extraSamples = toml.getBoolean(EXTRA_SAMPLES_FIELD_NAME, () -> false);
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Extra samples must be a boolean" + e);
        }
    }

    @Override
    public @NotNull Sampler newSampler(@NotNull IFeatureModel featureModel) {
        return new  BaitalSampler(featureModel, this.sampleSize, this.t, this.strategy, this.rounds, this.extraSamples);
    }
}
