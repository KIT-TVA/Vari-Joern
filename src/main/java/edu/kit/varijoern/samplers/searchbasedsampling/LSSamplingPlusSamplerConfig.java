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

    // Fields for the configuration of the LS-Sampling-Plus sampler.
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
    public LSSamplingPlusSamplerConfig(@NotNull TomlTable toml) throws InvalidConfigException {
        super(toml);

        // Target sample size.
        this.sampleSize = TomlUtils.getMandatoryInt(SAMPLE_SIZE_FIELD_NAME, toml, "Sample size is " +
                "missing or invalid.");
        if (this.sampleSize <= 0) {
            throw new InvalidConfigException("Sample size must be => 1.");
        }

        // Target feature interaction coverage.
        this.t = TomlUtils.getMandatoryInt(T_FIELD_NAME, toml, "Parameter t is missing or invalid.");
        if (this.t <= 1) {
            throw new InvalidConfigException("Parameter t must be >= 2.");
        }

        // Number of candidates per iteration.
        long lambda;
        try {
            lambda = toml.getLong(LAMBDA_FIELD_NAME, () -> 100);
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Lambda must be an integer value.", e);
        }
        if (lambda < 0 || lambda > Integer.MAX_VALUE) {
            throw new InvalidConfigException(String.format("Lambda must >= 1 and <= %d.", Integer.MAX_VALUE));
        }
        this.lambda = (int) lambda;

        // Cardinality of the measuring set.
        long delta;
        try {
            delta = toml.getLong(DELTA_FIELD_NAME, () -> 1_000_000);
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Delta must be an integer value.", e);
        }
        if (delta < 0 || delta > Integer.MAX_VALUE) {
            throw new InvalidConfigException(String.format("Delta must be >= 1 and <= %d.", Integer.MAX_VALUE));
        }
        this.delta = (int) delta;
    }

    @Override
    public @NotNull Sampler newSampler(@NotNull IFeatureModel featureModel) {
        return new LSSamplingPlusSampler(featureModel, this.sampleSize, this.t, this.lambda, this.delta);
    }
}


