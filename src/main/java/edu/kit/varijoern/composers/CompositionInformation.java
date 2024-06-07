package edu.kit.varijoern.composers;

import edu.kit.varijoern.composers.sourcemap.SourceMap;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Contains information about a composer pass, such as the location of the resulting code.
 */
public class CompositionInformation {
    private final @NotNull Path location;
    private final @NotNull Map<String, Boolean> enabledFeatures;
    private final @NotNull PresenceConditionMapper presenceConditionMapper;
    private final @NotNull SourceMap sourceMap;
    private final @NotNull List<LanguageInformation> languageInformation;

    /**
     * Creates a new {@link CompositionInformation} instance.
     *
     * @param location                the location of the composed code. Must be an absolute path.
     *                                See {@link CompositionInformation#getLocation()}.
     * @param enabledFeatures         a map of feature names to their enabled status at the time of composition
     * @param presenceConditionMapper a {@link PresenceConditionMapper} for this composition result
     * @param sourceMap               a {@link SourceMap} for this composition result
     * @param languageInformation     relevant details about how the languages in the composition should be handled
     */
    public CompositionInformation(@NotNull Path location, @NotNull Map<String, Boolean> enabledFeatures,
                                  @NotNull PresenceConditionMapper presenceConditionMapper,
                                  @NotNull SourceMap sourceMap,
                                  @NotNull List<LanguageInformation> languageInformation) {
        this.location = location;
        this.enabledFeatures = enabledFeatures;
        this.presenceConditionMapper = presenceConditionMapper;
        this.sourceMap = sourceMap;
        this.languageInformation = languageInformation;
    }

    /**
     * Returns the location of the directory containing the composed code as an absolute path. Only source files should
     * be present in this directory and its subdirectories.
     *
     * @return the location of the directory containing the composed code
     */
    public @NotNull Path getLocation() {
        return location;
    }

    /**
     * Returns a map of feature names to their enabled status at the time of composition.
     *
     * @return a map of feature names to their enabled status at the time of composition
     */
    public @NotNull Map<String, Boolean> getEnabledFeatures() {
        return this.enabledFeatures;
    }

    /**
     * Returns the presence condition mapper for the composition result.
     *
     * @return a presence condition mapper
     */
    public @NotNull PresenceConditionMapper getPresenceConditionMapper() {
        return presenceConditionMapper;
    }

    /**
     * Returns the source map for the composition result.
     *
     * @return a source map
     */
    public @NotNull SourceMap getSourceMap() {
        return sourceMap;
    }

    /**
     * Returns the language information for the composition result.
     *
     * @return a list of language information
     */
    public @NotNull List<LanguageInformation> getLanguageInformation() {
        return languageInformation;
    }
}
