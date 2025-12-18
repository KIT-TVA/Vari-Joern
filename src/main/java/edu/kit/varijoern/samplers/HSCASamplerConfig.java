package edu.kit.varijoern.samplers;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.TomlUtils;
import org.jetbrains.annotations.NotNull;
import org.tomlj.TomlInvalidTypeException;
import org.tomlj.TomlTable;

/**
 * Contains the configuration of the HSCA sampler.
 */
public class HSCASamplerConfig extends SamplerConfig {
    private static final String T_FIELD_NAME = "t";
    private static final String CUTOFF_TIME_FIELD_NAME = "cutoff-time";
    private static final String L_FIELD_NAME = "l";

    private final int t;
    private final int cutoffTime;
    private final int l;

    /**
     * Creates a new {@link HSCASamplerConfig} by extracting data from the specified TOML section.
     *
     * @param toml the TOML section
     * @throws InvalidConfigException if the TOML section does not represent a valid configuration
     */
    protected HSCASamplerConfig(@NotNull TomlTable toml) throws InvalidConfigException {
        super(toml);
        this.t = TomlUtils.getMandatoryInt(T_FIELD_NAME, toml, "Parameter t is missing or invalid");
        if (this.t <= 1) {
            throw new InvalidConfigException("Parameter t must be >= 2");
        }

        long cutoffTime;
        try {
            cutoffTime = toml.getLong(CUTOFF_TIME_FIELD_NAME, () -> 60);
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Cutoff time must be an integer value", e);
        }
        if (cutoffTime <= 0 || cutoffTime > Integer.MAX_VALUE) {
            throw new InvalidConfigException("cutoff-time must be >= 1 and <= " + Integer.MAX_VALUE);
        }
        this.cutoffTime = (int) cutoffTime;

        long l;
        try {
            l = toml.getLong(L_FIELD_NAME, () -> 5000);
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Parameter l must be an integer value", e);
        }
        if (l <= 0 || l > Integer.MAX_VALUE) {
            throw new InvalidConfigException("Parameter l must be >= 1 and <= " + Integer.MAX_VALUE);
        }
        this.l = (int) l;
    }

    @Override
    public @NotNull Sampler newSampler(@NotNull IFeatureModel featureModel) {
        return new HSCASampler(featureModel, this.t, this.cutoffTime, this.l);
    }
}
