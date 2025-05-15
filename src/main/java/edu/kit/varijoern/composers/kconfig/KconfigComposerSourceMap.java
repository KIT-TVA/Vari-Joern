package edu.kit.varijoern.composers.kconfig;

import edu.kit.varijoern.composers.sourcemap.SourceLocation;
import edu.kit.varijoern.composers.sourcemap.SourceMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A {@link SourceMap} that maps locations in the composed code to locations in the original code using the generation
 * information provided by a {@link KconfigComposer}.
 */
public class KconfigComposerSourceMap implements SourceMap {
    private static final @NotNull Logger LOGGER = LogManager.getLogger();

    private final @NotNull Map<Path, GenerationInformation> generationInformation;
    private final Path tmpSourceDirectory;
    private final @NotNull Map<Path, LineDirectiveSourceMap> lineDirectiveSourceMaps;

    /**
     * Creates a new {@link KconfigComposerSourceMap} instance.
     *
     * @param generationInformation a map of paths to generation information. The paths are relative to the directory
     *                              containing the composed code.
     * @param tmpSourceDirectory    the temporary directory containing the original source files and their build
     *                              artifacts, as produced by the {@link KconfigComposer}.
     */
    public KconfigComposerSourceMap(@NotNull Map<Path, GenerationInformation> generationInformation,
                                    Path tmpSourceDirectory) {
        LOGGER.info("Creating source map");
        this.generationInformation = generationInformation;
        this.tmpSourceDirectory = tmpSourceDirectory;
        this.lineDirectiveSourceMaps = generationInformation.values().stream()
                .map(GenerationInformation::originalPath)
                .collect(Collectors.toSet())
                .stream()
                .map(originalPath -> {
                    Path absoluteOriginalPath = tmpSourceDirectory.resolve(originalPath);
                    try {
                        return Map.entry(originalPath, new LineDirectiveSourceMap(absoluteOriginalPath));
                    } catch (IOException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public @NotNull Optional<SourceLocation> getOriginalLocation(@NotNull SourceLocation location) {
        GenerationInformation generationInformation = this.generationInformation.get(location.file());
        if (generationInformation == null) {
            return Optional.empty();
        }
        SourceLocation sourceFileLocation = new SourceLocation(
                generationInformation.originalPath(),
                location.line() - generationInformation.addedLines()
        );
        LineDirectiveSourceMap lineDirectiveSourceMap = this.lineDirectiveSourceMaps.get(sourceFileLocation.file());
        if (lineDirectiveSourceMap == null) {
            return Optional.of(sourceFileLocation);
        }
        Optional<SourceLocation> originalLocationOptional
                = lineDirectiveSourceMap.getOriginalLocation(sourceFileLocation.line());
        if (originalLocationOptional.isEmpty())
            return Optional.empty();
        SourceLocation originalLocation = originalLocationOptional.get();

        if (originalLocation.file().isAbsolute()) {
            originalLocation = new SourceLocation(
                    this.tmpSourceDirectory.relativize(originalLocation.file()),
                    originalLocation.line()
            );
        }
        return Optional.of(originalLocation);
    }
}
