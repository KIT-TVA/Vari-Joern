package edu.kit.varijoern.composers.kconfig.conditionmapping;

import edu.kit.varijoern.composers.conditionmapping.OriginalFilePresenceConditionMapper;
import edu.kit.varijoern.composers.conditionmapping.OriginalLinePresenceConditionMapper;
import edu.kit.varijoern.composers.conditionmapping.PresenceConditionMapper;
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
 * A {@link PresenceConditionMapper} that combines the presence conditions of the original files and the
 * presence conditions of the lines in the generated files. The presence condition of a line in a generated file is
 * the conjunction of the presence condition of the file and the presence condition of the line in the original
 * file.
 */
public class KbuildPresenceConditionMapper implements PresenceConditionMapper {
    private static final Logger LOGGER = LogManager.getLogger();
    private final @NotNull OriginalFilePresenceConditionMapper filePresenceConditionMapper;
    private final @NotNull Map<Path, ? extends OriginalLinePresenceConditionMapper> linePresenceConditionMappers;
    private final @NotNull SourceMap sourceMap;

    /**
     * Creates a new {@link KbuildPresenceConditionMapper} from the specified components. The line presence condition
     * mappers are specified individually for each generated file. This allows multiple line presence condition mappers
     * to exist for the same original file, as the same original file can be used to generate multiple generated files
     * with different preprocessor options, potentially leading to different presence conditions.
     *
     * @param filePresenceConditionMapper  a file presence condition mapper for the original files
     * @param linePresenceConditionMappers the {@link OriginalLinePresenceConditionMapper}s for the individual files.
     *                                     The keys are the paths to the generated files, relative to the output
     *                                     directory of the composer.
     * @param sourceMap                    the source map mapping source locations in the composer output to the
     *                                     original source code
     */
    public KbuildPresenceConditionMapper(@NotNull OriginalFilePresenceConditionMapper filePresenceConditionMapper,
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
            LOGGER.debug("No file presence condition found for {}", file);
            return Optional.empty();
        }

        OriginalLinePresenceConditionMapper linePresenceConditionMapper = this.linePresenceConditionMappers
                .get(file.normalize());
        if (linePresenceConditionMapper == null) {
            LOGGER.debug("No line presence condition found for {}:{}", file, lineNumber);
            return Optional.empty();
        }

        return linePresenceConditionMapper.getPresenceCondition(originalLocation.line())
                .map(linePresenceCondition -> new And(filePresenceCondition.get(), linePresenceCondition));
    }
}
