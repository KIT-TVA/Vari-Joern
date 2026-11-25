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
 * Contains the configuration of the HSCA sampler.
 */
public class HSCASamplerConfig extends SamplerConfig {
    // Literals used in the TOML file for the configuration of the HSCA sampler.
    private static final String T_FIELD_NAME = "t";
    private static final String L_FIELD_NAME = "L";
    private static final String CUTOFF_TIME_FIELD_NAME = "cutoff-time";
    private static final String USE_GROUP_FIELD_NAME = "use-group";
    private static final String USE_PRIORITY_FIELD_NAME = "use-priority";
    private static final String USE_SECOND_OPTIMIZATION_FIELD_NAME = "use-second-optimization";

    // Fields for the configuration of the HSCA sampler.
    private final int t;
    private final int l;
    private final int cutoffTime;
    private final boolean useGroup;
    private final boolean usePriority;
    private final boolean useSecondOptimization;

    /**
     * Creates a new {@link HSCASamplerConfig} by extracting data from the specified TOML section.
     *
     * @param toml the TOML section
     * @throws InvalidConfigException if the TOML section does not represent a valid configuration
     */
    public HSCASamplerConfig(@NotNull TomlTable toml) throws InvalidConfigException {
        super(toml);

        // Target feature interaction coverage.
        this.t = TomlUtils.getMandatoryInt(HSCASamplerConfig.T_FIELD_NAME, toml,
                "Parameter t is missing or invalid.");
        if (this.t <= 1) {
            throw new InvalidConfigException("Parameter t must be >= 2.");
        }

        // Termination criterion for the first optimization pass (default: 5_000).
        long l;
        try {
            l = toml.getLong(HSCASamplerConfig.L_FIELD_NAME, () -> 5_000L);
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Parameter L must be an integer value.", e);
        }
        if (l <= 0L || l > Integer.MAX_VALUE) {
            throw new InvalidConfigException(String.format("Parameter L must be >= 1 and <= %d.", Integer.MAX_VALUE));
        }
        this.l = (int) l;

        // Cutoff time for the second optimization pass (default: 1_000).
        long cutoffTime;
        try {
            cutoffTime = toml.getLong(HSCASamplerConfig.CUTOFF_TIME_FIELD_NAME, () -> 1_000L);
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Cutoff time must be an integer value.", e);
        }
        if (cutoffTime <= 0L || cutoffTime > Integer.MAX_VALUE) {
            throw new InvalidConfigException(String.format("Cutoff time must be >= 1 and <= %d.", Integer.MAX_VALUE));
        }
        this.cutoffTime = (int) cutoffTime;

        // Whether to use variable grouping strategy (default: true).
        try {
            this.useGroup = toml.getBoolean(HSCASamplerConfig.USE_GROUP_FIELD_NAME, () -> true);
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Use group toggle must be a boolean.", e);
        }

        // Whether to use dynamic priority assigning technique (default: true).
        try {
            this.usePriority = toml.getBoolean(HSCASamplerConfig.USE_PRIORITY_FIELD_NAME, () -> true);
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Use priority toggle must be a boolean.", e);
        }

        // Whether the second optimization pass is enabled (default: true).
        try {
            this.useSecondOptimization = toml.getBoolean(HSCASamplerConfig.USE_SECOND_OPTIMIZATION_FIELD_NAME,
                    () -> true);
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Second optimization toggle must be a boolean.", e);
        }
    }

    @Override
    public @NotNull Sampler newSampler(@NotNull IFeatureModel featureModel) {
        return new HSCASampler(featureModel, this.t,
                this.l, this.cutoffTime, this.useGroup, this.usePriority, this.useSecondOptimization);
    }
}
