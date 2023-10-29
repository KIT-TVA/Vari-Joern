package edu.kit.varijoern.composers;

import edu.kit.varijoern.IllegalFeatureNameException;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Path;
import java.util.Map;

public class KbuildComposer implements Composer {
    private final Path sourcePath;

    public KbuildComposer(Path sourcePath) {
        this.sourcePath = sourcePath;
    }

    @Override
    public @NotNull CompositionInformation compose(@NotNull Map<String, Boolean> features, @NotNull Path destination,
                                                   @NotNull Path tmpPath)
        throws IllegalFeatureNameException, IOException, ComposerException {
        Path tmpSourcePath = tmpPath.resolve("source");
        try {
            this.copySourceTo(tmpSourcePath);
            this.generateConfig(features, tmpSourcePath);
            return null;
        } finally {
            FileUtils.deleteDirectory(tmpSourcePath.toFile());
        }
    }

    private void generateConfig(Map<String, Boolean> features, Path tmpSourcePath) throws IOException {
        Path configPath = tmpSourcePath.resolve(".config");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(configPath.toFile(), false))) {
            for (Map.Entry<String, Boolean> feature : features.entrySet()) {
                writer.write("CONFIG_%s=%s\n".formatted(feature.getKey(), feature.getValue() ? "y" : "n"));
            }
        }
    }

    private void copySourceTo(Path tmpSourcePath) throws IOException {
        FileUtils.copyDirectory(this.sourcePath.toFile(), tmpSourcePath.toFile());
    }
}
