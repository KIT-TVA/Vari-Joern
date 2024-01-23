package edu.kit.varijoern.samplers;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.config.InvalidConfigException;
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
    private final List<String> features;

    /**
     * Creates a new {@link FixedSamplerConfig} by extracting data from the specified TOML section.
     *
     * @param toml the TOML section
     * @throws InvalidConfigException if the TOML section does not represent a valid configuration
     */
    public FixedSamplerConfig(TomlTable toml) throws InvalidConfigException {
        super(toml);
        if (!toml.isArray(FEATURES_FIELD_NAME))
            throw new InvalidConfigException("Features for fixed sampler are missing or not an array");
        TomlArray featuresTomlArray = Objects.requireNonNull(toml.getArray(FEATURES_FIELD_NAME));
        List<String> features = new ArrayList<>();
        for (int i = 0; i < featuresTomlArray.size(); i++) {
            try {
                features.add(featuresTomlArray.getString(i));
            } catch (TomlInvalidTypeException e) {
                throw new InvalidConfigException("One of the features for the fixed sampler is not a string", e);
            }
        }
        this.features = List.copyOf(features);
    }

    @Override
    public Sampler newSampler(IFeatureModel featureModel) {
        return new FixedSampler(this.features, featureModel);
    }

    /**
     * Returns the feature combination to be returned by the sampler as specified by the configuration file.
     *
     * @return the feature combination
     */
    public List<String> getFeatures() {
        return features;
    }
}
