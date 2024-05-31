package edu.kit.varijoern.composers;

import edu.kit.varijoern.composers.sourcemap.SourceMap;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Contains information about a composer pass, such as the location of the resulting code.
 */
public class CompositionInformation {
    private final Path location;
    private final Map<String, Boolean> enabledFeatures;
    private final PresenceConditionMapper presenceConditionMapper;
    private final SourceMap sourceMap;
    private final List<LanguageInformation> languageInformation;

    /**
     * Creates a new {@link CompositionInformation} instance.
     *
     * @param location                the location of the composed code.
     *                                See {@link CompositionInformation#getLocation()}.
     * @param enabledFeatures         a map of feature names to their enabled status at the time of composition
     * @param presenceConditionMapper a {@link PresenceConditionMapper} for this composition result
     * @param sourceMap               a {@link SourceMap} for this composition result
     * @param languageInformation     relevant details about how the languages in the composition should be handled
     */
    public CompositionInformation(Path location, Map<String, Boolean> enabledFeatures,
                                  PresenceConditionMapper presenceConditionMapper, SourceMap sourceMap,
                                  List<LanguageInformation> languageInformation) {
        this.location = location;
        this.enabledFeatures = enabledFeatures;
        this.presenceConditionMapper = presenceConditionMapper;
        this.sourceMap = sourceMap;
        this.languageInformation = languageInformation;
    }

    /**
     * Returns the location of the directory containing the composed code. Only source files should be present in this
     * directory and its subdirectories.
     *
     * @return the location of the directory containing the composed code
     */
    public Path getLocation() {
        return location;
    }

    /**
     * Returns a map of feature names to their enabled status at the time of composition.
     *
     * @return a map of feature names to their enabled status at the time of composition
     */
    public Map<String, Boolean> getEnabledFeatures() {
        return this.enabledFeatures;
    }

    /**
     * Returns the presence condition mapper for the composition result.
     *
     * @return a presence condition mapper
     */
    public PresenceConditionMapper getPresenceConditionMapper() {
        return presenceConditionMapper;
    }

    /**
     * Returns the source map for the composition result.
     *
     * @return a source map
     */
    public SourceMap getSourceMap() {
        return sourceMap;
    }

    /**
     * Returns the language information for the composition result.
     *
     * @return a list of language information
     */
    public List<LanguageInformation> getLanguageInformation() {
        return languageInformation;
    }
}
