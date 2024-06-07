package edu.kit.varijoern.composers.kbuild;

import edu.kit.varijoern.composers.sourcemap.SourceLocation;
import edu.kit.varijoern.composers.sourcemap.SourceMap;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * A {@link SourceMap} that maps locations in the composed code to locations in the original code using the generation
 * information provided by a {@link KbuildComposer}.
 */
public class KbuildComposerSourceMap implements SourceMap {
    private final @NotNull Map<Path, GenerationInformation> generationInformation;

    /**
     * Creates a new {@link KbuildComposerSourceMap} instance.
     *
     * @param generationInformation a map of paths to generation information. The paths are relative to the directory
     *                              containing the composed code.
     */
    public KbuildComposerSourceMap(@NotNull Map<Path, GenerationInformation> generationInformation) {
        this.generationInformation = generationInformation;
    }

    @Override
    public @NotNull Optional<SourceLocation> getOriginalLocation(@NotNull SourceLocation location) {
        GenerationInformation generationInformation = this.generationInformation.get(location.file());
        if (generationInformation == null) {
            return Optional.empty();
        }
        return Optional.of(new SourceLocation(
                generationInformation.originalPath(),
                location.line() - generationInformation.addedLines()
        ));
    }
}
