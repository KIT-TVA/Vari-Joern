package edu.kit.varijoern.samplers.featureinteractionsampling;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.TomlUtils;
import edu.kit.varijoern.samplers.Sampler;
import edu.kit.varijoern.samplers.SamplerConfig;
import org.jetbrains.annotations.NotNull;
import org.tomlj.TomlInvalidTypeException;
import org.tomlj.TomlTable;

/**
 * Contains the configuration of the YASA sampler.
 */
public class YASASamplerConfig extends SamplerConfig {
    // Literals used in the TOML file for the configuration of the YASA sampler.
    private static final String T_FIELD_NAME = "t";
    private static final String SAMPLE_SIZE_FIELD_NAME = "max-samples";
    private static final String ITERATIONS_FIELD_NAME = "iterations";

    // Fields for the configuration of the YASA sampler.
    private final int t;
    private final int maxSampleSize;
    private final int iterations;

    /**
     * Creates a new {@link YASASamplerConfig} by extracting data from the specified TOML section.
     *
     * @param toml the TOML section.
     * @throws InvalidConfigException if the TOML section does not represent a valid configuration.
     */
    public YASASamplerConfig(@NotNull TomlTable toml) throws InvalidConfigException {
        super(toml);

        // Target feature interaction coverage.
        this.t = TomlUtils.getMandatoryInt(YASASamplerConfig.T_FIELD_NAME, toml, "Parameter t is " +
                "missing or invalid.");
        if (this.t <= 0) {
            throw new InvalidConfigException("Parameter t must be >= 1.");
        }

        // Maximum sample size (default value: Integer.MAX_VALUE).
        long maxSampleSize;
        try {
            maxSampleSize = toml.getLong(YASASamplerConfig.SAMPLE_SIZE_FIELD_NAME, () -> (long) Integer.MAX_VALUE);
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Sample size must be an integer value.", e);
        }
        if (maxSampleSize < 0 || maxSampleSize > Integer.MAX_VALUE) {
            throw new InvalidConfigException(String.format("Maximum sample size must be >= 0 and < %d.",
                    Integer.MAX_VALUE));
        }
        this.maxSampleSize = (int) maxSampleSize;

        // Iterations (default value: 5).
        long iterations;
        try {
            iterations = toml.getLong(YASASamplerConfig.ITERATIONS_FIELD_NAME, () -> 5L);
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Number of iterations must be an integer value.", e);
        }
        if (iterations < 0 || iterations > Integer.MAX_VALUE) {
            throw new InvalidConfigException(String.format("Number of iterations must be >= 0 and < %d.",
                    Integer.MAX_VALUE));
        }
        this.iterations = (int) iterations;
    }

    @Override
    public @NotNull Sampler newSampler(@NotNull IFeatureModel featureModel) {
        return new YASASampler(featureModel, this.t, this.maxSampleSize, this.iterations);
    }
}
