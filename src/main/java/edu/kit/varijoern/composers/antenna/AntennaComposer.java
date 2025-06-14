package edu.kit.varijoern.composers.antenna;

import antenna.preprocessor.v3.PPException;
import antenna.preprocessor.v3.Preprocessor;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.composers.Composer;
import edu.kit.varijoern.composers.ComposerException;
import edu.kit.varijoern.composers.CompositionInformation;
import edu.kit.varijoern.composers.GenericLanguageInformation;
import edu.kit.varijoern.composers.sourcemap.IdentitySourceMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A composer that runs the Antenna preprocessor on each {@code .java} file in a directory and its subdirectories.
 */
public class AntennaComposer implements Composer {
    public static final String NAME = "antenna";
    private static final Logger LOGGER = LogManager.getLogger();

    private final @NotNull Path sourceLocation;
    private final boolean shouldSkipPresenceConditionExtraction;

    /**
     * Creates a new {@link AntennaComposer} which preprocesses all files in the specified root directory.
     *
     * @param sourceLocation                        the root directory. Must be an absolute path.
     * @param shouldSkipPresenceConditionExtraction whether the presence condition extraction should be skipped
     */
    public AntennaComposer(@NotNull Path sourceLocation, boolean shouldSkipPresenceConditionExtraction) {
        this.sourceLocation = sourceLocation;
        this.shouldSkipPresenceConditionExtraction = shouldSkipPresenceConditionExtraction;
    }

    @Override
    public @NotNull CompositionInformation compose(@NotNull Map<String, Boolean> features, @NotNull Path destination,
                                                   @NotNull IFeatureModel featureModel)
            throws IOException, ComposerException {
        LOGGER.info("Running Antenna composer");
        List<String> enabledFeatures = features.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .toList();
        Preprocessor preprocessor = new Preprocessor(null, null);
        try {
            preprocessor.addDefines(String.join(",", enabledFeatures));
        } catch (PPException e) {
            throw new ComposerException(e);
        }

        ConditionTreePresenceConditionMapper presenceConditionMapper = new ConditionTreePresenceConditionMapper();

        if (!this.shouldSkipPresenceConditionExtraction) {
            try (Stream<Path> sourceFiles = Files.walk(this.sourceLocation)) {
                for (Path sourcePath : (Iterable<Path>) sourceFiles::iterator) {
                    if (!sourcePath.getFileName().toString().endsWith(".java")) continue;
                    if (!Files.isRegularFile(sourcePath)) continue;

                    Path relativePath = this.sourceLocation.relativize(sourcePath);
                    Path destinationPath = destination.resolve(relativePath);

                    Vector<String> lineVector = new Vector<>(Files.readAllLines(sourcePath, StandardCharsets.UTF_8));

                    try {
                        preprocessor.preprocess(
                                lineVector,
                                StandardCharsets.UTF_8.name()
                        );
                    } catch (PPException e) {
                        throw new ComposerException(String.format("Could not preprocess %s", relativePath), e);
                    }

                    Files.createDirectories(destinationPath.getParent());
                    Files.writeString(destinationPath,
                            lineVector.stream().collect(Collectors.joining(System.lineSeparator()))
                    );

                    presenceConditionMapper.tryAddFile(relativePath, lineVector.stream().toList());
                }
            }
        }

        LOGGER.info("Composer finished successfully");

        return new CompositionInformation(
                destination,
                features,
                presenceConditionMapper,
                new IdentitySourceMap(),
                List.of(new GenericLanguageInformation())
        );
    }
}
