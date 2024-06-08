package edu.kit.varijoern.samplers;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.config.InvalidConfigException;
import org.jetbrains.annotations.NotNull;
import org.tomlj.TomlArray;
import org.tomlj.TomlInvalidTypeException;
import org.tomlj.TomlTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Contains the configuration of the fixed sampler.
 */
public class FixedSamplerConfig extends SamplerConfig {
    private static final String FEATURES_FIELD_NAME = "features";
    private final @NotNull List<List<String>> sample;

    /**
     * Creates a new {@link FixedSamplerConfig} by extracting data from the specified TOML section.
     *
     * @param toml the TOML section
     * @throws InvalidConfigException if the TOML section does not represent a valid configuration
     */
    public FixedSamplerConfig(@NotNull TomlTable toml) throws InvalidConfigException {
        super(toml);
        if (!toml.isArray(FEATURES_FIELD_NAME))
            throw new InvalidConfigException("Features for fixed sampler are missing or not an array");
        TomlArray configurationsTomlArray = Objects.requireNonNull(toml.getArray(FEATURES_FIELD_NAME));
        List<List<String>> configurations = new ArrayList<>();
        for (int i = 0; i < configurationsTomlArray.size(); i++) {
            TomlArray featuresTomlArray;
            try {
                featuresTomlArray = configurationsTomlArray.getArray(i);
            } catch (TomlInvalidTypeException e) {
                throw new InvalidConfigException("One of the configurations for the fixed sampler is not an array", e);
            }
            List<String> features = new ArrayList<>();
            for (int j = 0; j < featuresTomlArray.size(); j++) {
                try {
                    features.add(featuresTomlArray.getString(j));
                } catch (TomlInvalidTypeException e) {
                    throw new InvalidConfigException(
                            "One of the features of configuration %d for the fixed sampler is not a string."
                                    .formatted(i),
                            e
                    );
                }
            }
            configurations.add(features);
        }
        this.sample = List.copyOf(configurations);
    }

    @Override
    public @NotNull Sampler newSampler(@NotNull IFeatureModel featureModel) {
        return new FixedSampler(this.sample, featureModel);
    }

    /**
     * Returns the sample to be returned by the sampler as specified by the configuration file.
     *
     * @return the features enabled in each of the configurations. One entry of the top-level list corresponds to one
     * configuration.
     */
    public @NotNull List<List<String>> getSample() {
        return this.sample;
    }
}
