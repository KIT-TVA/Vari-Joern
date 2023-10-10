package edu.kit.varijoern.composers.antenna;

import antenna.preprocessor.v3.PPException;
import antenna.preprocessor.v3.Preprocessor;
import edu.kit.varijoern.IllegalFeatureNameException;
import edu.kit.varijoern.composers.Composer;
import edu.kit.varijoern.composers.ComposerException;
import edu.kit.varijoern.composers.CompositionInformation;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A composer that runs the Antenna preprocessor on each {@code .java} file in a directory and its subdirectories.
 */
public class AntennaComposer implements Composer {
    public static final String NAME = "antenna";

    private final Path sourceLocation;

    /**
     * Creates a new {@link AntennaComposer} which preprocesses all files in the specified root directory.
     *
     * @param sourceLocation the root directory
     */
    public AntennaComposer(Path sourceLocation) {
        this.sourceLocation = sourceLocation;
    }

    @Override
    public @NotNull CompositionInformation compose(@NotNull List<String> features, @NotNull Path destination)
        throws IllegalFeatureNameException, IOException, ComposerException {
        Preprocessor preprocessor = new Preprocessor(null, null);
        try {
            preprocessor.addDefines(String.join(",", features));
        } catch (PPException e) {
            throw new IllegalFeatureNameException(e);
        }
        ConditionTreeFeatureMapper featureMapper = new ConditionTreeFeatureMapper();

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

                featureMapper.tryAddFile(relativePath, lineVector.stream().toList());
            }
        }
        return new CompositionInformation(destination, features, featureMapper);
    }
}
