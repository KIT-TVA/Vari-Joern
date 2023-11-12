package edu.kit.varijoern.composers.kbuild;

import edu.kit.varijoern.IllegalFeatureNameException;
import edu.kit.varijoern.composers.Composer;
import edu.kit.varijoern.composers.ComposerException;
import edu.kit.varijoern.composers.CompositionInformation;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

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
            System.err.println("Copying source");
            this.copySourceTo(tmpSourcePath);
            System.err.println("Generating .config");
            this.generateConfig(features, tmpSourcePath);
            System.err.println("Running make prepare");
            this.runMake(tmpSourcePath, "prepare");
            System.err.println("Determining files to be included");
            Map<Path, IncludedFile> includedFiles = this.getIncludedFiles(tmpSourcePath);
            return null;
        } finally {
            FileUtils.deleteDirectory(tmpSourcePath.toFile());
        }
    }

    private void generateConfig(Map<String, Boolean> features, Path tmpSourcePath)
        throws IOException, ComposerException {
        Path configPath = tmpSourcePath.resolve(".config");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(configPath.toFile(), false))) {
            for (Map.Entry<String, Boolean> feature : features.entrySet()) {
                writer.write("CONFIG_%s=%s\n".formatted(feature.getKey(), feature.getValue() ? "y" : "n"));
            }
        }

        this.runMake(tmpSourcePath, "olddefconfig");
    }

    private Map<Path, IncludedFile> getIncludedFiles(Path tmpSourcePath) throws IOException {
        ProcessBuilder makeProcessBuilder = new ProcessBuilder("make", "-in")
            .directory(tmpSourcePath.toFile());
        int makeExitCode;
        String output;
        try {
            Process makeProcess = makeProcessBuilder.start();
            output = IOUtils.toString(makeProcess.getInputStream(), Charset.defaultCharset());
            makeExitCode = makeProcess.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException("make -in was interrupted", e);
        }

        return null;
    }

    private void runMake(Path tmpSourcePath, String... args) throws ComposerException, IOException {
        ProcessBuilder makeProcessBuilder = new ProcessBuilder(
            Stream.concat(Stream.of("make"), Arrays.stream(args))
                .toList())
            .inheritIO()
            .directory(tmpSourcePath.toFile());
        int makeExitCode;
        try {
            makeExitCode = makeProcessBuilder.start().waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException("Unexpected interruption of make process", e);
        }
        if (makeExitCode != 0)
            throw new ComposerException("Make failed with exit code %d".formatted(makeExitCode));
    }


    private void copySourceTo(Path tmpSourcePath) throws IOException {
        FileUtils.copyDirectory(this.sourcePath.toFile(), tmpSourcePath.toFile());
    }

    private record IncludedFile(Optional<GCCCall> gccCall) {
    }
}
