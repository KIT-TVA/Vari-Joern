package edu.kit.varijoern.composers.conditionmapping;

import edu.kit.varijoern.composers.PresenceConditionMapper;
import edu.kit.varijoern.composers.sourcemap.SourceLocation;
import edu.kit.varijoern.composers.sourcemap.SourceMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.prop4j.And;
import org.prop4j.Node;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * A {@link PresenceConditionMapper} that combines a {@link SourceMap}, an {@link OriginalFilePresenceConditionMapper}
 * and multiple {@link OriginalLinePresenceConditionMapper}s.
 */
public class CombinedPresenceConditionMapper implements PresenceConditionMapper {
    private static final Logger LOGGER = LogManager.getLogger();
    private final @NotNull OriginalFilePresenceConditionMapper filePresenceConditionMapper;
    private final @NotNull Map<Path, ? extends OriginalLinePresenceConditionMapper> linePresenceConditionMappers;
    private final @NotNull SourceMap sourceMap;

    /**
     * Creates a new {@link CombinedPresenceConditionMapper} from the specified components.
     *
     * @param filePresenceConditionMapper  a file presence condition mapper for the original files
     * @param linePresenceConditionMappers the {@link OriginalLinePresenceConditionMapper}s for the individual files.
     *                                     This list should contain an entry for all files for which the composer
     *                                     detected a GCC call. The keys are the paths to the original source files,
     *                                     relative to the composer's source directory.
     * @param sourceMap                    the source map mapping source locations in the composer output to the
     *                                     original source code
     */
    public CombinedPresenceConditionMapper(@NotNull OriginalFilePresenceConditionMapper filePresenceConditionMapper,
                                           @NotNull Map<Path, ? extends OriginalLinePresenceConditionMapper>
                                                   linePresenceConditionMappers,
                                           @NotNull SourceMap sourceMap) {
        this.filePresenceConditionMapper = filePresenceConditionMapper;
        this.linePresenceConditionMappers = linePresenceConditionMappers;
        this.sourceMap = sourceMap;
    }

    @Override
    public @NotNull Optional<Node> getPresenceCondition(@NotNull Path file, int lineNumber) {
        Optional<SourceLocation> originalLocationOptional = this.sourceMap.getOriginalLocation(
                new SourceLocation(file, lineNumber)
        );
        if (originalLocationOptional.isEmpty()) {
            LOGGER.warn("No original location found for {}:{}", file, lineNumber);
            return Optional.empty();
        }
        SourceLocation originalLocation = originalLocationOptional.get();

        Optional<Node> filePresenceCondition = this.filePresenceConditionMapper
                .getPresenceCondition(originalLocation.file());
        if (filePresenceCondition.isEmpty()) {
            LOGGER.warn("No file presence condition found for {}", file);
            return Optional.empty();
        }

        OriginalLinePresenceConditionMapper linePresenceConditionMapper = this.linePresenceConditionMappers
                .get(originalLocation.file().normalize());
        if (linePresenceConditionMapper == null) {
            LOGGER.warn("No line presence condition found for {}:{}", file, lineNumber);
            return Optional.empty();
        }

        return linePresenceConditionMapper.getPresenceCondition(originalLocation.line())
                .map(linePresenceCondition -> new And(filePresenceCondition.get(), linePresenceCondition));
    }
}
