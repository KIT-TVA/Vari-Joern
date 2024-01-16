package edu.kit.varijoern.composers.kbuild;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.IFeatureModelElement;
import edu.kit.varijoern.composers.Composer;
import edu.kit.varijoern.composers.ComposerException;
import edu.kit.varijoern.composers.CompositionInformation;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class KbuildComposer implements Composer {
    public static final String NAME = "kbuild";
    private static final Pattern OPTION_NAME_VALUE_PATTERN = Pattern.compile("CONFIG_(\\w+)=.*");
    private static final Pattern OPTION_NOT_SET_PATTERN = Pattern.compile("# CONFIG_(\\w+) is not set");
    private static final Set<String> supportedSystems = Set.of("linux", "busybox");

    private final Path sourcePath;
    private final String system;
    private KbuildFeatureMapperCreator featureMapperCreator = null;

    public KbuildComposer(Path sourcePath, String system) {
        this.sourcePath = sourcePath;
        this.system = system;
    }

    @Override
    public @NotNull CompositionInformation compose(@NotNull Map<String, Boolean> features, @NotNull Path destination,
                                                   @NotNull Path tmpPath, @NotNull IFeatureModel featureModel)
            throws IOException, ComposerException {
        if (this.featureMapperCreator == null) {
            this.featureMapperCreator = new KbuildFeatureMapperCreator(this.sourcePath, this.system,
                    tmpPath, featureModel);
        }

        Path tmpSourcePath = tmpPath.resolve("source");
        try {
            this.copySourceTo(tmpSourcePath);
            this.generateConfig(features, tmpSourcePath);
            Set<Dependency> includedFiles = this.getIncludedFiles(tmpSourcePath);
            Map<Path, GenerationInformation> generationInformation = this.generateFiles(
                    includedFiles, destination, tmpSourcePath
            );
            Map<Path, LineFeatureMapper> lineFeatureMappers = this.createLineFeatureMappers(
                    generationInformation,
                    includedFiles,
                    featureModel.getFeatures().stream()
                            .map(IFeatureModelElement::getName)
                            .collect(Collectors.toSet()),
                    tmpSourcePath
            );
            return new CompositionInformation(
                    destination,
                    features,
                    this.featureMapperCreator.createFeatureMapper(generationInformation, lineFeatureMappers)
            );
        } finally {
            FileUtils.deleteDirectory(tmpSourcePath.toFile());
        }
    }

    private void generateConfig(Map<String, Boolean> features, Path tmpSourcePath)
            throws IOException, ComposerException {
        System.out.println("Generating .config");
        this.runMake(tmpSourcePath, "defconfig");

        Set<String> remainingFeatures = new HashSet<>(features.keySet());
        Path configPath = tmpSourcePath.resolve(".config");
        List<String> defaultConfigLines = Files.readAllLines(configPath);
        defaultConfigLines.replaceAll(line -> {
            String optionName;
            Matcher nameValueMatcher = OPTION_NAME_VALUE_PATTERN.matcher(line);
            if (nameValueMatcher.matches()) {
                optionName = nameValueMatcher.group(1);
            } else {
                Matcher notSetMatcher = OPTION_NOT_SET_PATTERN.matcher(line);
                if (notSetMatcher.matches()) {
                    optionName = notSetMatcher.group(1);
                } else {
                    return line;
                }
            }
            if (features.containsKey(optionName)) {
                remainingFeatures.remove(optionName);
                return formatOption(optionName, features.get(optionName));
            }

            return line;
        });
        for (String remainingFeature : remainingFeatures) {
            defaultConfigLines.add(formatOption(remainingFeature, features.get(remainingFeature)));
        }
        Files.write(configPath, defaultConfigLines);
    }

    private String formatOption(String optionName, boolean activated) {
        if (activated)
            return "CONFIG_%s=y".formatted(optionName);
        else
            return "# CONFIG_%s is not set".formatted(optionName);
    }

    private Set<Dependency> getIncludedFiles(Path tmpSourcePath) throws IOException, ComposerException {
        System.out.println("Determining files to be included");
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
        if (!Files.exists(tmpSourcePath.resolve(inclusionInformation.filePath()))) {
            System.err.printf("File %s does not exist, skipping%n", inclusionInformation.filePath());
            return List.of();
        }
        System.out.printf("Getting dependencies of %s%n", inclusionInformation.filePath());
        String makeRule = this.getFileMakeRule(inclusionInformation, tmpSourcePath);
        String dependencyList = makeRule.substring(makeRule.indexOf(':') + 1);
        Stream<String> dependencies = Arrays.stream(dependencyList.replace("\\", "").split("\\s+"))
                .filter(dependency -> !dependency.isBlank());
        // This is only an approximation. The files included earlier don't see the files included later. This should be
        // reflected in this list, but since we don't know the order of the includes, we can't do that.
        List<Dependency> dependencyInformation = dependencies
                // Dependencies with absolute paths likely don't belong to the project.
                .filter(dep -> !Path.of(dep).isAbsolute())
                .map(dep -> {
                    if (dep.endsWith(".h")) {
                        return new HeaderDependency(dep);
                    } else {
                        return new CompiledDependency(new InclusionInformation(
                                Path.of(dep),
                                inclusionInformation.includedFiles().stream()
                                        .filter(includedFile -> !includedFile.equals(dep))
                                        .collect(Collectors.toSet()),
                                inclusionInformation.defines(),
                                inclusionInformation.includePaths()
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
        gccCall.addAll(inclusionInformation.includePaths().stream()
                .map(includePath -> "-I" + includePath)
                .toList());
        gccCall.addAll(inclusionInformation.defines().entrySet().stream()
                .map(entry -> "-D" + entry.getKey() + "=" + entry.getValue())
                .toList());
        gccCall.addAll(inclusionInformation.includedFiles().stream()
                .flatMap(includedFile -> Stream.of("-include", includedFile))
                .toList());
        gccCall.add("-M");
        gccCall.add("-MG");
        gccCall.add(inclusionInformation.filePath().toString());

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

    private Map<Path, GenerationInformation> generateFiles(Set<Dependency> dependencies, Path destination,
                                                           Path tmpSourcePath)
            throws IOException, ComposerException {
        System.out.println("Generating files");
        Map<Path, List<Dependency>> targets = new HashMap<>();
        for (Dependency dependency : dependencies) {
            targets.computeIfAbsent(dependency.getFilePath(), k -> new ArrayList<>()).add(dependency);
        }
        Map<Path, GenerationInformation> generationInformation = new HashMap<>();
        for (Map.Entry<Path, List<Dependency>> target : targets.entrySet()) {
            generationInformation.putAll(
                    this.generateFile(target.getKey(), target.getValue(), destination, tmpSourcePath)
            );
        }
        return generationInformation;
    }

    private Map<Path, GenerationInformation> generateFile(Path filePath, List<Dependency> configurations,
                                                          Path destination, Path tmpSourcePath)
            throws IOException {
        Path sourcePath = tmpSourcePath.resolve(filePath);
        if (!Files.exists(sourcePath)) {
            System.err.printf("File %s does not exist, skipping%n", filePath);
            return Map.of();
        }
        Path destinationPath = destination.resolve(filePath);
        Files.createDirectories(destinationPath.getParent());
        Map<Path, GenerationInformation> generationInformation = new HashMap<>();
        boolean copied = false;
        boolean generated = false;
        for (Dependency configuration : configurations) {
            if (configuration instanceof CompiledDependency) {
                Map.Entry<Path, GenerationInformation> fileGenerationInformation =
                        this.generateFileWithPreprocessorDirectives(
                                ((CompiledDependency) configuration).getInclusionInformation(),
                                destination,
                                tmpSourcePath
                        );
                generationInformation.put(fileGenerationInformation.getKey(), fileGenerationInformation.getValue());
                generated = true;
            } else if (configuration instanceof HeaderDependency) {
                if (!copied) {
                    System.out.printf("Copying %s%n", filePath);
                    Files.copy(sourcePath, destinationPath);
                    copied = true;
                    generationInformation.put(filePath, new GenerationInformation(filePath, 0));
                }
            }
        }
        if (!generated && !copied) {
            throw new RuntimeException("File %s was neither copied nor generated".formatted(filePath));
        }
        return generationInformation;
    }

    private Map.Entry<Path, GenerationInformation> generateFileWithPreprocessorDirectives(
            InclusionInformation inclusionInformation, Path destination, Path tmpSourcePath) throws IOException {
        System.out.printf("Generating %s with preprocessor directives%n", inclusionInformation.filePath());
        String fileName = inclusionInformation.filePath().getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = dotIndex == -1 ? fileName : fileName.substring(0, dotIndex);
        String extension = dotIndex == -1 ? "" : fileName.substring(dotIndex);
        Path relativeDestinationPath = inclusionInformation.filePath().getParent()
                .resolve(baseName + "-" + inclusionInformation.hashCode() + extension);
        Path destinationPath = destination
                .resolve(relativeDestinationPath);

        try (FileOutputStream stream = new FileOutputStream(destinationPath.toFile())) {
            for (Map.Entry<String, String> define : inclusionInformation.defines().entrySet()) {
                stream.write(("#define %s %s%n".formatted(define.getKey(), define.getValue())).getBytes(StandardCharsets.US_ASCII));
            }
            for (String include : inclusionInformation.includedFiles()) {
                Path includePath = inclusionInformation.filePath().getParent().relativize(Path.of(include));
                stream.write(("#include \"%s\"%n".formatted(includePath)).getBytes(StandardCharsets.US_ASCII));
            }
            stream.write(Files.readAllBytes(tmpSourcePath.resolve(inclusionInformation.filePath())));
        }
        return Map.entry(relativeDestinationPath, new GenerationInformation(
                inclusionInformation.filePath().normalize(),
                inclusionInformation.defines().size() + inclusionInformation.includedFiles().size()
        ));
    }

    private Map<Path, LineFeatureMapper> createLineFeatureMappers(Map<Path, GenerationInformation> generationInformation,
                                                                  Set<Dependency> dependencies,
                                                                  Set<String> knownFeatures,
                                                                  Path tmpSourcePath)
            throws IOException, ComposerException {
        if (!LineFeatureMapper.isSupportedSystem(this.system)) {
            System.out.printf("System %s is not supported, skipping line feature mapper creation%n", this.system);
            return Map.of();
        }

        System.out.println("Creating line feature mappers");

        // Clean up to ensure that the header file containing config definitions doesn't exist
        this.runMake(tmpSourcePath, "clean");

        Map<Path, LineFeatureMapper> lineFeatureMappers = new HashMap<>();
        Map<Path, Dependency> dependenciesByPath = dependencies.stream()
                .collect(Collectors.toMap(Dependency::getFilePath, dependency -> dependency));
        for (Map.Entry<Path, GenerationInformation> entry : generationInformation.entrySet()) {
            Path generatedFilePath = entry.getKey();
            GenerationInformation fileGenerationInformation = entry.getValue();
            Dependency dependency = dependenciesByPath.get(fileGenerationInformation.originalPath());
            if (!(dependency instanceof CompiledDependency)) {
                continue;
            }
            System.out.printf("Creating line feature mapper for %s%n", entry.getKey());
            InclusionInformation inclusionInformation = ((CompiledDependency) dependency).getInclusionInformation();
            lineFeatureMappers.put(
                    generatedFilePath,
                    new LineFeatureMapper(inclusionInformation, tmpSourcePath, fileGenerationInformation.addedLines(),
                            knownFeatures, this.system)
            );
        }

        return lineFeatureMappers;
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
        System.out.println("Copying source");
        FileUtils.copyDirectory(this.sourcePath.toFile(), tmpSourcePath.toFile());
    }

    public static boolean isSupportedSystem(String system) {
        return supportedSystems.contains(system);
    }

    private static abstract class Dependency {
        public abstract Path getFilePath();
    }

    private static class CompiledDependency extends Dependency {
        private final InclusionInformation inclusionInformation;

        public CompiledDependency(InclusionInformation inclusionInformation) {
            this.inclusionInformation = inclusionInformation;
        }

        @Override
        public Path getFilePath() {
            return this.inclusionInformation.filePath();
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
        private final Path filePath;

        public HeaderDependency(String filePath) {
            this.filePath = Path.of(filePath).normalize();
        }

        @Override
        public Path getFilePath() {
            return this.filePath;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            HeaderDependency that = (HeaderDependency) o;
            return Objects.equals(filePath, that.filePath);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.filePath);
        }
    }
}
