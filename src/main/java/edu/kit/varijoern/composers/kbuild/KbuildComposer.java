package edu.kit.varijoern.composers.kbuild;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.IFeatureModelElement;
import edu.kit.varijoern.composers.CCPPLanguageInformation;
import edu.kit.varijoern.composers.Composer;
import edu.kit.varijoern.composers.ComposerException;
import edu.kit.varijoern.composers.CompositionInformation;
import jodd.io.StreamGobbler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.io.IoBuilder;
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

/**
 * A composer for Kconfig-Kbuild-based systems. It copies the files required by the specified variant to the output directory
 * and adds {@code #define} and {@code #include} directives to C files because it is not possible to pass command-line includes and
 * defines to Joern.
 * <p>
 * The composer uses kmax to determine the presence conditions of the individual files
 * (see {@link KbuildPresenceConditionMapperCreator}). The presence conditions of individual lines are determined using
 * SuperC (see {@link LinePresenceConditionMapper}).
 * <p>
 * Currently, only the Kbuild and Kconfig variants of Linux and Busybox are supported. For Linux, no presence
 * conditions can be determined at the moment.
 * <h2>How it works</h2>
 * <ol>
 *     <li>If not yet done, create a {@link KbuildPresenceConditionMapperCreator} which determines the presence conditions
 *     of most C files.</li>
 *     <li>Copy the source directory to a temporary directory.</li>
 *     <li>Generate a .config file based on the specified features:
 *     <ol>
 *         <li>Run {@code make defconfig} to generate a default .config file. This is used to set options not present
 *         in the feature model (e.g. because they aren't booleans) to their default values.</li>
 *         <li>Read the .config file and replace the values of the known options with the specified values.</li>
 *     </ol>
 *     </li>
 *     <li>Generate required header files (specifically {@code autoconf.h}) by invoking {@code make oldconfig}.</li>
 *     <li>Determine the set of files relevant to the variant:
 *     <ol>
 *         <li>Run {@code make -in} to determine the files explicitly compiled by {@code make} and the GCC flags they're compiled
 *         with.</li>
 *         <li>Run {@code gcc -M -MG} on each of the files determined in the previous step to determine the files they depend
 *         on. Files that are compiled with different include or define flags are treated as different dependencies.
 *         </li>
 *     </ol>
 *     </li>
 *     </li>
 *     <li>Copy the files determined in the previous step to the output directory and add {@code #define} and {@code #include}
 *     directives to non-header files.</li>
 *     <li>Determine the presence conditions of the individual lines of the files in the output directory. See
 *     {@link LinePresenceConditionMapper} for details.</li>
 * </ol>
 *
 * <h2>Issues</h2>
 * <ul>
 *     <li>The extracted presence conditions need not be accurate:
 *     <ul>
 *         <li>The presence conditions of lines are determined using the include and define arguments to their
 *         respective GCC calls. These can vary depending on the variant analyzed and since the conditions of the
 *         compiler flags can't be determined, the extracted presence conditions can vary as well.</li>
 *         <li>See {@link KbuildPresenceConditionMapperCreator} and {@link LinePresenceConditionMapper} for more information.</li>
 *     </ul>
 *     </li>
 *     <li>Header files are copied to the output directory without adding define and include directives. This is
 *     necessary because otherwise the number of files would be too large.</li>
 *     <li>Include paths specified as compiler arguments can't be passed to the analyzer.</li>
 *     <li>Files that are generated during the make build and their dependencies may be missing in the result.</li>
 * </ul>
 */
public class KbuildComposer implements Composer {
    public static final String NAME = "kbuild";
    private static final Pattern OPTION_NAME_VALUE_PATTERN = Pattern.compile("CONFIG_(\\w+)=.*");
    private static final Pattern OPTION_NOT_SET_PATTERN = Pattern.compile("# CONFIG_(\\w+) is not set");
    private static final Pattern HEADER_FILE_PATTERN = Pattern.compile(".*\\.(?:h|H|hpp|hxx|h++)");
    private static final Set<String> supportedSystems = Set.of("linux", "busybox");
    private static final Logger logger = LogManager.getLogger();
    private static final OutputStream streamLogger = IoBuilder.forLogger().setLevel(Level.DEBUG).buildOutputStream();

    private final String system;
    private final Path tmpPath;
    private final Path tmpSourcePath;
    private KbuildPresenceConditionMapperCreator presenceConditionMapperCreator = null;

    /**
     * Creates a new {@link KbuildComposer} which will create variants from the specified source directory.
     *
     * @param sourcePath the path to the source directory
     * @param system     the variant of the Kbuild/Kconfig system. Use {@link KbuildComposer#isSupportedSystem(String)} to
     *                   determine if a given system is supported.
     * @param tmpPath    a {@link Path} to a temporary directory that can be used by the composer
     */
    public KbuildComposer(Path sourcePath, String system, Path tmpPath) throws IOException, ComposerException {
        this.system = system;
        this.tmpPath = tmpPath;
        this.tmpSourcePath = this.tmpPath.resolve("source");
        this.copySourceTo(sourcePath, this.tmpSourcePath);
        // Make sure that there are no compilation artifacts.
        // These would break dependency detection because make would not try to recompile them.
        this.runMake("distclean");
        if (this.system.equals("busybox")) {
            // BusyBox's Kbuild variant allows to specify Kbuild information in the source files. Since kmax cannot
            // handle this, we use `make gen_build_files` to generate Kbuild files.
            this.runMake("gen_build_files");
        }
    }

    @Override
    public @NotNull CompositionInformation compose(@NotNull Map<String, Boolean> features, @NotNull Path destination,
                                                   @NotNull IFeatureModel featureModel)
            throws IOException, ComposerException {
        if (this.presenceConditionMapperCreator == null) {
            this.presenceConditionMapperCreator = new KbuildPresenceConditionMapperCreator(this.tmpSourcePath, this.system,
                    this.tmpPath, featureModel);
        }

        this.generateConfig(features, tmpSourcePath);
        Set<Dependency> includedFiles = this.getIncludedFiles(tmpSourcePath);
        Map<Path, GenerationInformation> generationInformation = this.generateFiles(
                includedFiles, destination, tmpSourcePath
        );
        Map<Path, LinePresenceConditionMapper> linePresenceConditionMappers = this.createLinePresenceConditionMappers(
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
                this.presenceConditionMapperCreator.createPresenceConditionMapper(generationInformation,
                        linePresenceConditionMappers),
                new KbuildComposerSourceMap(generationInformation),
                List.of(new CCPPLanguageInformation(
                        includedFiles.stream()
                                .filter(dependency -> dependency instanceof CompiledDependency)
                                .map(dependency -> ((CompiledDependency) dependency).getInclusionInformation())
                                .collect(Collectors.toMap(InclusionInformation::getComposedFilePath,
                                        InclusionInformation::includePaths))
                ))
        );
    }

    /**
     * Generates a .config file based on the specified features and ensures that the {@code include/autoconf.h} file exists
     * and is up-to-date.
     *
     * @param features      the enabled and disabled features
     * @param tmpSourcePath the path to the temporary source directory
     * @throws IOException       if an I/O error occurs
     * @throws ComposerException if the .config file could not be generated
     */
    private void generateConfig(Map<String, Boolean> features, Path tmpSourcePath)
            throws IOException, ComposerException {
        logger.info("Generating .config");
        this.runMake("defconfig");

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

        // Make sure that `include/autoconf.h` is generated
        this.runMake("oldconfig");
    }

    /**
     * Creates a line for a .config file containing the specified option.
     *
     * @param optionName the name of the option
     * @param activated  whether the option is activated
     * @return the line that can be used in a .config file
     */
    private String formatOption(String optionName, boolean activated) {
        if (activated)
            return "CONFIG_%s=y".formatted(optionName);
        else
            return "# CONFIG_%s is not set".formatted(optionName);
    }

    /**
     * Determines the files that are included in the variant and the compiler flags they are compiled with.
     *
     * @param tmpSourcePath the path to the temporary source directory
     * @return the files that are included in the variant
     * @throws IOException       if an I/O error occurs
     * @throws ComposerException if the files could not be determined
     */
    private Set<Dependency> getIncludedFiles(Path tmpSourcePath) throws IOException, ComposerException {
        logger.info("Determining files to be included");
        ProcessBuilder makeProcessBuilder = new ProcessBuilder("make", "-in")
                .directory(tmpSourcePath.toFile());
        int makeExitCode;
        String output;
        try {
            logger.debug("Running make -in");
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
            logger.debug("Parsing gcc calls");
            gccCalls = new GCCCallExtractor(output).getCalls();
        } catch (ParseException e) {
            throw new ComposerException("gcc calls could not be parsed", e);
        }
        logger.debug("Found {} gcc calls", gccCalls.size());
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

    /**
     * Determines the dependencies of the specified files.
     *
     * @param compiledFiles the files to determine the dependencies of
     * @param tmpSourcePath the path to the temporary source directory
     * @return the dependencies of the specified files
     * @throws ComposerException if the dependencies could not be determined
     * @throws IOException       if an I/O error occurs
     */
    private Set<Dependency> getDependencies(List<InclusionInformation> compiledFiles, Path tmpSourcePath)
            throws ComposerException, IOException {
        logger.info("Getting dependencies");
        Set<Dependency> dependencies = new HashSet<>();
        for (InclusionInformation compiledFile : compiledFiles) {
            dependencies.add(new CompiledDependency(compiledFile));
            dependencies.addAll(this.getDependenciesOfFile(compiledFile, tmpSourcePath));
        }
        logger.debug("Found {} dependencies in total", dependencies.size());
        return dependencies;
    }

    /**
     * Determines the dependencies of the specified file. For non-header files, the compiler flags used for compiling
     * the specified file are included.
     *
     * @param inclusionInformation the file to determine the dependencies of
     * @param tmpSourcePath        the path to the temporary source directory
     * @return the dependencies of the specified file
     * @throws IOException       if an I/O error occurs
     * @throws ComposerException if the dependencies could not be determined
     */
    private List<Dependency> getDependenciesOfFile(InclusionInformation inclusionInformation, Path tmpSourcePath)
            throws IOException, ComposerException {
        if (!Files.exists(tmpSourcePath.resolve(inclusionInformation.filePath()))) {
            logger.warn("File {} does not exist, skipping dependency calculation",
                    inclusionInformation.filePath());
            return List.of();
        }
        logger.info("Getting dependencies of {}", inclusionInformation.filePath());
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
                    if (HEADER_FILE_PATTERN.matcher(dep).matches()) {
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
        logger.debug("Found {} dependencies", dependencyInformation.size());
        return dependencyInformation;
    }

    /**
     * Calls GCC with the specified compiler flags to determine the dependencies of the specified file. The dependencies
     * using the syntax of a make rule. Dependencies of files generated during a full build may be missed.
     *
     * @param inclusionInformation the file to determine the dependencies of
     * @param tmpSourcePath        the path to the temporary source directory
     * @return the make rule describing the dependencies of the specified file
     * @throws IOException       if an I/O error occurs
     * @throws ComposerException if GCC fails
     */
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
            logger.error("GCC failed with the following error: {}", error);
            logger.error("Its output was: {}", output);
            logger.error("The command was: {}", String.join(" ", gccCall));
            throw new ComposerException("gcc failed with exit code %d".formatted(gccExitCode));
        }
        return output;
    }

    /**
     * Generates the files required by the specified variant using {@code generateFile}.
     *
     * @param dependencies  the files required by the variant
     * @param destination   the path to the output directory
     * @param tmpSourcePath the path to the temporary source directory
     * @return a map from the paths of the generated files to information about the generation process
     * @throws IOException if an I/O error occurs
     */
    private Map<Path, GenerationInformation> generateFiles(Set<Dependency> dependencies, Path destination,
                                                           Path tmpSourcePath)
            throws IOException {
        logger.info("Generating files");
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

    /**
     * Generates the specified file. Header files are only copied, whereas other files are generated by adding
     * {@code #define} and {@code #include} directives to the original files.
     *
     * @param filePath       the path to the file to generate, relative to the temporary and output source directories
     * @param configurations information about which variants of the file to generate (e.g. with which preprocessor
     *                       directives)
     * @param destination    the path to the output directory
     * @param tmpSourcePath  the path to the temporary source directory
     * @return a map from the paths of the generated files to information about the generation process
     * @throws IOException if an I/O error occurs
     */
    private Map<Path, GenerationInformation> generateFile(Path filePath, List<Dependency> configurations,
                                                          Path destination, Path tmpSourcePath)
            throws IOException {
        Path sourcePath = tmpSourcePath.resolve(filePath);
        if (!Files.exists(sourcePath)) {
            logger.warn("File {} does not exist, not generating.", filePath);
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
                    logger.debug("Copying {}", filePath);
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
        logger.debug("Generating {} with preprocessor directives", inclusionInformation.filePath());
        Path relativeDestinationPath = inclusionInformation.getComposedFilePath();
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

    private Map<Path, LinePresenceConditionMapper> createLinePresenceConditionMappers(
            Map<Path, GenerationInformation> generationInformation,
            Set<Dependency> dependencies,
            Set<String> knownFeatures,
            Path tmpSourcePath)
            throws IOException, ComposerException {
        if (!LinePresenceConditionMapper.isSupportedSystem(this.system)) {
            logger.warn("System {} is not supported, skipping line presence condition mapper creation",
                    this.system);
            return Map.of();
        }

        logger.info("Creating line presence condition mappers");

        // Clean up to ensure that the header file containing config definitions doesn't exist
        this.runMake("clean");

        Map<Path, LinePresenceConditionMapper> linePresenceConditionMappers = new HashMap<>();
        Map<Path, Dependency> dependenciesByPath = dependencies.stream()
                .collect(Collectors.toMap(Dependency::getFilePath, dependency -> dependency));
        for (Map.Entry<Path, GenerationInformation> entry : generationInformation.entrySet()) {
            Path generatedFilePath = entry.getKey();
            GenerationInformation fileGenerationInformation = entry.getValue();
            Dependency dependency = dependenciesByPath.get(fileGenerationInformation.originalPath());
            if (!(dependency instanceof CompiledDependency)) {
                continue;
            }
            logger.debug("Creating line presence condition mapper for {}", entry.getKey());
            InclusionInformation inclusionInformation = ((CompiledDependency) dependency).getInclusionInformation();
            linePresenceConditionMappers.put(
                    generatedFilePath,
                    new LinePresenceConditionMapper(inclusionInformation, tmpSourcePath,
                            fileGenerationInformation.addedLines(), knownFeatures, this.system)
            );
        }

        return linePresenceConditionMappers;
    }

    /**
     * A helper function to run make with the specified arguments with the temporary source directory as the working
     * directory.
     *
     * @param args the arguments to pass to make
     * @throws ComposerException if make returns a non-zero exit code
     * @throws IOException       if an I/O error occurs
     */
    private void runMake(String... args) throws ComposerException, IOException {
        Process makeProcessBuilder = new ProcessBuilder(
                Stream.concat(Stream.of("make"), Arrays.stream(args))
                        .toList())
                .directory(this.tmpSourcePath.toFile())
                .start();
        StreamGobbler stdoutGobbler = new StreamGobbler(makeProcessBuilder.getInputStream(), streamLogger);
        StreamGobbler stderrGobbler = new StreamGobbler(makeProcessBuilder.getErrorStream(), streamLogger);
        stdoutGobbler.start();
        stderrGobbler.start();
        int makeExitCode;
        try {
            makeExitCode = makeProcessBuilder.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException("Unexpected interruption of make process", e);
        }
        if (makeExitCode != 0)
            throw new ComposerException("Make failed with exit code %d".formatted(makeExitCode));
    }

    private void copySourceTo(Path originalSourcePath, Path tmpSourcePath) throws IOException {
        logger.info("Copying source");
        FileUtils.copyDirectory(originalSourcePath.toFile(), tmpSourcePath.toFile(), file -> !file.getName().equals(".git"));
    }

    /**
     * Determines whether the specified variant of Kbuild/Kconfig is supported.
     *
     * @param system the variant to check
     * @return if the variant is supported
     */
    public static boolean isSupportedSystem(String system) {
        return supportedSystems.contains(system);
    }

    /**
     * Contains information about a file included in the variant.
     */
    private static abstract class Dependency {
        /**
         * Returns the path to the file, relative to the source directory.
         *
         * @return the path to the file
         */
        public abstract Path getFilePath();
    }

    /**
     * Contains information about a non-header file included in the variant, specifically the flags used to compile it.
     */
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

    /**
     * Contains minimal information about a header file included in the variant.
     */
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
