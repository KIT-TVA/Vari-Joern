package edu.kit.varijoern.featuremodel;

import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.NamedComponentConfig;
import org.tomlj.TomlTable;

/**
 * The base class for all feature model reader configurations.
 */
public abstract class FeatureModelReaderConfig extends NamedComponentConfig {
    /**
     * Creates a new {@link FeatureModelReaderConfig} by extracting data from the specified TOML section.
     *
     * @param toml the TOML section
     * @throws InvalidConfigException if the TOML section does not represent a valid analyzer configuration
     */
    protected FeatureModelReaderConfig(TomlTable toml) throws InvalidConfigException {
        super(toml);
    }

    /**
     * Instantiates a new {@link FeatureModelReader}.
     *
     * @return the new {@link FeatureModelReader}
     */
    public abstract FeatureModelReader newFeatureModelReader();

    @Override
    public String getComponentType() {
        return "feature model reader";
    }
}
