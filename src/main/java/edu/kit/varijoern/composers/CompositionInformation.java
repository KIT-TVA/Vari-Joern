package edu.kit.varijoern.composers;

import edu.kit.varijoern.composers.conditionmapping.PresenceConditionMapper;
import edu.kit.varijoern.composers.sourcemap.SourceMap;
import edu.kit.varijoern.samplers.Configuration;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

/**
 * Contains information about a composer pass, such as the location of the resulting code.
 */
public class CompositionInformation {
    private final @NotNull Path location;
    private final @NotNull Configuration configuration;
    private final @NotNull PresenceConditionMapper presenceConditionMapper;
    private final @NotNull SourceMap sourceMap;
    private final @NotNull List<LanguageInformation> languageInformation;

    /**
     * Creates a new {@link CompositionInformation} instance.
     *
     * @param location                the location of the composed code. Must be an absolute path.
     *                                See {@link CompositionInformation#getLocation()}.
     * @param configuration           the configuration of the variant the composer should compose
     * @param presenceConditionMapper a {@link PresenceConditionMapper} for this composition result
     * @param sourceMap               a {@link SourceMap} for this composition result
     * @param languageInformation     relevant details about how the languages in the composition should be handled
     */
    public CompositionInformation(@NotNull Path location, @NotNull Configuration configuration,
                                  @NotNull PresenceConditionMapper presenceConditionMapper,
                                  @NotNull SourceMap sourceMap,
                                  @NotNull List<LanguageInformation> languageInformation) {
        this.location = location;
        this.configuration = configuration;
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
     * Returns the configuration of the variant the composer should compose.
     *
     * @return the configuration of the variant the composer should compose
     */
    public @NotNull Configuration getConfiguration() {
        return this.configuration;
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
