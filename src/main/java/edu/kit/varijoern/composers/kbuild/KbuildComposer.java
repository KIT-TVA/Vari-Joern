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
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class KbuildComposer implements Composer {
    public static final String NAME = "kbuild";

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
            System.out.println("Copying source");
            this.copySourceTo(tmpSourcePath);
            System.out.println("Generating .config");
            this.generateConfig(features, tmpSourcePath);
            System.out.println("Running make prepare");
            this.runMake(tmpSourcePath, "prepare");
            System.out.println("Determining files to be included");
            Set<Dependency> includedFiles = this.getIncludedFiles(tmpSourcePath);
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

    private Set<Dependency> getIncludedFiles(Path tmpSourcePath) throws IOException, ComposerException {
        ProcessBuilder makeProcessBuilder = new ProcessBuilder("make", "-in")
            .directory(tmpSourcePath.toFile());
        int makeExitCode;
        String output;
        try {
            System.out.println("Running make -in");
            Process makeProcess = makeProcessBuilder.start();
            output = IOUtils.toString(makeProcess.getInputStream(), Charset.defaultCharset());
            makeExitCode = makeProcess.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException("make -in was interrupted", e);
        }
        if (makeExitCode != 0)
            throw new ComposerException("make -in failed with exit code %d".formatted(makeExitCode));
        List<GCCCall> gccCalls;
        try {
            System.out.println("Parsing gcc calls");
            gccCalls = new GCCCallExtractor(output).getCalls();
        } catch (ParseException e) {
            throw new ComposerException("gcc calls could not be parsed", e);
        }
        System.out.printf("Found %d gcc calls%n", gccCalls.size());
        List<InclusionInformation> compiledFiles = new ArrayList<>();
        for (GCCCall gccCall : gccCalls) {
            for (String file : gccCall.compiledFiles()) {
                compiledFiles.add(new InclusionInformation(
                    Path.of(file),
                    new HashSet<>(gccCall.includes()),
                    gccCall.defines(),
                    gccCall.includePaths()
                ));
            }
        }

        return this.getDependencies(compiledFiles, tmpSourcePath);
    }

    private Set<Dependency> getDependencies(List<InclusionInformation> compiledFiles, Path tmpSourcePath)
        throws ComposerException, IOException {
        System.out.println("Getting dependencies");
        Set<Dependency> dependencies = new HashSet<>();
        for (InclusionInformation compiledFile : compiledFiles) {
            dependencies.add(new CompiledDependency(compiledFile));
            dependencies.addAll(this.getDependenciesOfFile(compiledFile, tmpSourcePath));
        }
        System.out.printf("Found %d dependencies in total%n", dependencies.size());
        return dependencies;
    }

    private List<Dependency> getDependenciesOfFile(InclusionInformation inclusionInformation, Path tmpSourcePath)
        throws IOException, ComposerException {
        if (!Files.exists(tmpSourcePath.resolve(inclusionInformation.fileName))) {
            System.err.printf("File %s does not exist, skipping%n", inclusionInformation.fileName);
            return List.of();
        }
        System.out.printf("Getting dependencies of %s%n", inclusionInformation.fileName);
        String makeRule = this.getFileMakeRule(inclusionInformation, tmpSourcePath);
        String dependencyList = makeRule.substring(makeRule.indexOf(':') + 1);
        Stream<String> dependencies = Arrays.stream(dependencyList.replace("\\", "").split("\\s+"))
            .filter(dependency -> !dependency.isBlank());
        // This is only an approximation. The files included earlier don't see the files included later. This should be
        // reflected in this list, but since we don't know the order of the includes, we can't do that.
        List<Dependency> dependencyInformation = dependencies
            // Dependencies with absolute path likely don't belong to the project.
            .filter(dep -> !Path.of(dep).isAbsolute())
            .map(dep -> {
                if (dep.endsWith(".h")) {
                    return new HeaderDependency(dep);
                } else {
                    return new CompiledDependency(new InclusionInformation(
                        Path.of(dep),
                        inclusionInformation.includedFiles.stream()
                            .filter(includedFile -> !includedFile.equals(dep))
                            .collect(Collectors.toSet()),
                        inclusionInformation.defines,
                        inclusionInformation.includePaths
                    ));
                }
            })
            .toList();
        System.out.printf("Found %d dependencies%n", dependencyInformation.size());
        return dependencyInformation;
    }

    // This method may miss dependencies of generated files.
    private String getFileMakeRule(InclusionInformation inclusionInformation, Path tmpSourcePath)
        throws IOException, ComposerException {
        List<String> gccCall = new ArrayList<>();
        gccCall.add("gcc");
        gccCall.addAll(inclusionInformation.includePaths.stream()
            .map(includePath -> "-I" + includePath)
            .toList());
        gccCall.addAll(inclusionInformation.defines.entrySet().stream()
            .map(entry -> "-D" + entry.getKey() + "=" + entry.getValue())
            .toList());
        gccCall.addAll(inclusionInformation.includedFiles.stream()
            .flatMap(includedFile -> Stream.of("-include", includedFile))
            .toList());
        gccCall.add("-M");
        gccCall.add("-MG");
        gccCall.add(inclusionInformation.fileName.toString());

        ProcessBuilder gccProcessBuilder = new ProcessBuilder(gccCall)
            .directory(tmpSourcePath.toFile());
        int gccExitCode;
        String output;
        String error;
        try {
            Process gccProcess = gccProcessBuilder.start();
            output = IOUtils.toString(gccProcess.getInputStream(), Charset.defaultCharset());
            error = IOUtils.toString(gccProcess.getErrorStream(), Charset.defaultCharset());
            gccExitCode = gccProcess.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException("gcc was interrupted", e);
        }
        if (gccExitCode != 0) {
            System.err.println("GCC failed with the following error:");
            System.err.println(error);
            System.err.println("Its output was:");
            System.err.println(output);
            System.err.println("The command was:");
            System.err.println(String.join(" ", gccCall));
            throw new ComposerException("gcc failed with exit code %d".formatted(gccExitCode));
        }
        return output;
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

    private record InclusionInformation(Path fileName, Set<String> includedFiles, Map<String, String> defines,
                                        List<String> includePaths) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            InclusionInformation that = (InclusionInformation) o;
            return Objects.equals(fileName, that.fileName) && Objects.equals(includedFiles, that.includedFiles) && Objects.equals(defines, that.defines);
        }

        @Override
        public int hashCode() {
            return Objects.hash(fileName, includedFiles, defines);
        }
    }

    private static abstract class Dependency {
        public abstract String getFileName();
    }

    private static class CompiledDependency extends Dependency {
        private final InclusionInformation inclusionInformation;

        public CompiledDependency(InclusionInformation inclusionInformation) {
            this.inclusionInformation = inclusionInformation;
        }

        @Override
        public String getFileName() {
            return this.inclusionInformation.fileName.toString();
        }

        public InclusionInformation getInclusionInformation() {
            return this.inclusionInformation;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CompiledDependency that = (CompiledDependency) o;
            return Objects.equals(inclusionInformation, that.inclusionInformation);
        }

        @Override
        public int hashCode() {
            return Objects.hash(inclusionInformation);
        }
    }

    private static class HeaderDependency extends Dependency {
        private final String fileName;

        public HeaderDependency(String fileName) {
            this.fileName = fileName;
        }

        @Override
        public String getFileName() {
            return this.fileName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            HeaderDependency that = (HeaderDependency) o;
            return Objects.equals(fileName, that.fileName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(fileName);
        }
    }
}
