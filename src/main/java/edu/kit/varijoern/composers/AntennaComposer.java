package edu.kit.varijoern.composers;

import antenna.preprocessor.v3.PPException;
import antenna.preprocessor.v3.Preprocessor;
import edu.kit.varijoern.IllegalFeatureNameException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
    public CompositionInformation compose(@NotNull List<String> features, @NotNull Path destination)
        throws IllegalFeatureNameException, IOException, ComposerException {
        Preprocessor preprocessor = new Preprocessor(null, null);
        try {
            preprocessor.addDefines(String.join(",", features));
        } catch (PPException e) {
            throw new IllegalFeatureNameException(e);
        }

        try (Stream<Path> sourceFiles = Files.walk(this.sourceLocation)) {
            for (Path sourcePath : (Iterable<Path>) sourceFiles::iterator) {
                if (!sourcePath.getFileName().toString().endsWith(".java")) continue;
                if (!Files.isRegularFile(sourcePath)) continue;

                Path relativePath = this.sourceLocation.relativize(sourcePath);
                Path destinationPath = destination.resolve(relativePath);
                File sourceFile = new File(sourcePath.toString());
                File destinationFile = new File(destinationPath.toString());
                Files.createDirectories(destinationPath.getParent());
                //noinspection ResultOfMethodCallIgnored
                destinationFile.createNewFile();

                try {
                    preprocessor.preprocess(
                        new FileInputStream(sourceFile),
                        new FileOutputStream(destinationFile), "UTF8"
                    );
                } catch (PPException e) {
                    throw new ComposerException(String.format("Could not preprocess %s", relativePath), e);
                }
            }
        }
        return new CompositionInformation(destination, features);
    }
}
