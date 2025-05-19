package edu.kit.varijoern.composers.kconfig;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.IFeatureModelElement;
import edu.kit.varijoern.composers.CCPPLanguageInformation;
import edu.kit.varijoern.composers.Composer;
import edu.kit.varijoern.composers.ComposerException;
import edu.kit.varijoern.composers.CompositionInformation;
import edu.kit.varijoern.composers.conditionmapping.EmptyPresenceConditionMapper;
import edu.kit.varijoern.composers.conditionmapping.PresenceConditionMapper;
import edu.kit.varijoern.composers.kconfig.conditionmapping.KbuildFilePresenceConditionMapper;
import edu.kit.varijoern.composers.kconfig.conditionmapping.LinePresenceConditionMapper;
import edu.kit.varijoern.composers.kconfig.subjects.BusyboxStrategy;
import edu.kit.varijoern.composers.kconfig.subjects.ComposerStrategy;
import edu.kit.varijoern.composers.kconfig.subjects.ComposerStrategyFactory;
import edu.kit.varijoern.composers.sourcemap.SourceMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A composer for Kconfig-based systems. It copies the files required by the specified variant to the output
 * directory and adds {@code #define} and {@code #include} directives to C files because it is not possible to pass
 * command-line includes and definitions to Joern. In addition to composing the files, it also supports extracting
 * presence conditions of individual lines of code.
 * <p>
 * The exact commands used to compose a variant are subject-specific and implemented by a {@link ComposerStrategy}. The
 * composition process works as follows:
 * <ol>
 *     <li>Before the composition process starts, {@link ComposerStrategy#beforeComposition(IFeatureModel)} is called.
 *     This can be used for system-specific preparation steps.
 *     {@link BusyboxStrategy} uses this step to determine the presence
 *     conditions of most C files.</li>
 *     <li>Generate a .config file based on the specified features:
 *     <ol>
 *         <li>Run {@link ComposerStrategy#generateDefConfig()} to generate a default .config file. This is used to
 *         set options not present in the feature model (e.g., because they aren't booleans) to their default values.
 *         For example, {@link BusyboxStrategy} calls {@code make defconfig} to generate the default .config file.
 *         </li>
 *         <li>Read the .config file (from the path specified by {@link ComposerStrategy#getConfigPath()}), and replace
 *         the values of the options known from the feature model with the specified values, using the formats given
 *         by {@link ComposerStrategy#getOptionNameValuePattern()}, {@link ComposerStrategy#getOptionNotSetPattern()}
 *         and {@link ComposerStrategy#formatOption(String, boolean)}.</li>
 *         <li>Generate the header file containing the preprocessor macros corresponding to the configuration. This is
 *         done by {@link ComposerStrategy#processWrittenConfig()}.</li>
 *         <li>Perform a sanity check of the .config file to ensure that no options have been changed by the previous
 *         step.</li>
 *     </ol>
 *     </li>
 *     <li>Perform system-specific steps in preparation for the dependency detection by calling
 *     {@link ComposerStrategy#prepareDependencyDetection()}. For example, {@link BusyboxStrategy} uses this step to
 *     generate several required header files.</li>
 *     <li>Determine the set of files relevant to the variant:
 *     <ol>
 *         <li>Run {@code make -in} to determine the files explicitly compiled by {@code make} and the GCC flags they're
 *         compiled with.</li>
 *         <li>Run {@code gcc -M -MG} on each of the files determined in the previous step to determine the files they
 *         depend on. Files that are compiled with different include or define flags are treated as different
 *         dependencies.</li>
 *     </ol>
 *     </li>
 *     <li>Copy the files determined in the previous step to the output directory and add {@code #define} and
 *     {@code #include} directives to non-header files.</li>
 *     <li>Create a {@link KconfigComposerSourceMap} for the generated files.</li>
 *     <li>Determine the presence conditions of the source code lines by calling
 *     {@link ComposerStrategy#createPresenceConditionMapper(Stream, SourceMap, Set)}.</li>
 * </ol>
 *
 * <h2>Issues</h2>
 * <ul>
 *     <li>The extracted presence conditions may not be accurate:
 *     <ul>
 *         <li>Although it depends on the {@link ComposerStrategy}, the presence conditions of lines are usually
 *         determined using the include and define arguments to their respective GCC calls. These can vary depending on
 *         the variant analyzed, and since the conditions of the compiler flags can't be determined, the extracted
 *         presence conditions can vary as well.</li>
 *         <li>See {@link KbuildFilePresenceConditionMapper} and {@link LinePresenceConditionMapper} for more
 *         information.</li>
 *     </ul>
 *     </li>
 *     <li>Header files are copied to the output directory without adding define and include directives. This is
 *     necessary because otherwise the number of generated files would become too large.</li>
 *     <li>Files that are generated during the make build and their dependencies may be missing in the result.</li>
 * </ul>
 */
public class KconfigComposer implements Composer {
    public static final String NAME = "kconfig";
    // Root is the default root feature in feature models extracted by torte. __VISIBILITY__.* features are generated by
    // kmax when it encounters prompts.
    private static final Pattern IGNORED_FEATURES_PATTERN = Pattern.compile("^__VISIBILITY__.*|^Root$");
    private static final Pattern HEADER_FILE_PATTERN = Pattern.compile(".*\\.(?:h|H|hpp|hxx|h++)");
    private static final Logger LOGGER = LogManager.getLogger();

    private final @NotNull ComposerStrategy composerStrategy;
    private final @NotNull Charset encoding;
    private final @NotNull Path tmpPath;
    private final @NotNull Path tmpSourcePath;
    private final @NotNull Set<Path> presenceConditionExcludes;
    private final boolean shouldSkipPresenceConditionExtraction;

    /**
     * Creates a new {@link KconfigComposer} which will create variants from the specified source directory.
     *
     * @param sourcePath                the path to the source directory. Must be an absolute path.
     * @param composerStrategyFactory   a {@link ComposerStrategyFactory} for the variant of the Kconfig system
     * @param encoding                  the encoding of the source files
     * @param tmpPath                   a {@link Path} to a temporary directory that can be used by the composer. Must
     *                                  be absolute.
     * @param presenceConditionExcludes a list of paths to exclude from presence condition extraction. Must be relative
     *                                  to the source directory.
     * @throws IOException          if an I/O error occurs
     * @throws ComposerException    if the composer could not be created for another reason
     * @throws InterruptedException if the current thread is interrupted
     */
    public KconfigComposer(@NotNull Path sourcePath, @NotNull ComposerStrategyFactory composerStrategyFactory,
                           @NotNull Charset encoding, @NotNull Path tmpPath,
                           @NotNull Set<Path> presenceConditionExcludes, boolean shouldSkipPresenceConditionExtraction)
            throws IOException, ComposerException, InterruptedException {
        this.encoding = encoding;
        this.tmpPath = tmpPath;
        this.presenceConditionExcludes = presenceConditionExcludes
                .stream()
                .map(Path::normalize)
                .collect(Collectors.toSet());
        this.shouldSkipPresenceConditionExtraction = shouldSkipPresenceConditionExtraction;
        this.tmpSourcePath = this.tmpPath.resolve("source");
        this.composerStrategy = composerStrategyFactory.createComposerStrategy(this.tmpSourcePath, this.tmpPath,
                this.shouldSkipPresenceConditionExtraction, this.encoding);
        this.copySourceTo(sourcePath, this.tmpSourcePath);

        // Make sure that there are no compilation artifacts.
        // These would break dependency detection because make would not try to recompile them.
        this.composerStrategy.clean();
    }

    @Override
    public @NotNull CompositionInformation compose(@NotNull Map<String, Boolean> features, @NotNull Path destination,
                                                   @NotNull IFeatureModel featureModel)
            throws IOException, ComposerException, InterruptedException {
        this.composerStrategy.beforeComposition(featureModel);

        this.generateConfig(features);

        this.composerStrategy.prepareDependencyDetection();

        Set<Dependency> includedFiles = this.getIncludedFiles(tmpSourcePath);
        Map<Path, GenerationInformation> generationInformation = this.generateFiles(
                includedFiles, destination, tmpSourcePath
        );

        KconfigComposerSourceMap sourceMap = new KconfigComposerSourceMap(generationInformation, tmpSourcePath);
        PresenceConditionMapper presenceConditionMapper = this.createPresenceConditionMapper(generationInformation,
                includedFiles, sourceMap,
                featureModel.getFeatures().stream()
                        .map(IFeatureModelElement::getName)
                        .collect(Collectors.toSet())
        );

        CCPPLanguageInformation languageInformation = new CCPPLanguageInformation(
                includedFiles.stream()
                        .filter(dependency -> dependency instanceof CompiledDependency)
                        .map(dependency -> ((CompiledDependency) dependency).getInclusionInformation())
                        .collect(Collectors.toMap(InclusionInformation::getComposedFilePath,
                                InclusionInformation::includePaths)),
                includedFiles.stream()
                        .filter(dependency -> dependency instanceof CompiledDependency)
                        .map(dependency -> ((CompiledDependency) dependency).getInclusionInformation())
                        .collect(Collectors.toMap(InclusionInformation::getComposedFilePath,
                                InclusionInformation::systemIncludePaths))
        );

        return new CompositionInformation(
                destination,
                features,
                presenceConditionMapper,
                sourceMap,
                List.of(languageInformation)
        );
    }

    /**
     * Generates a .config file based on the specified features and ensures that the {@code include/autoconf.h} file
     * exists and is up to date.
     *
     * @param features the enabled and disabled features
     * @throws IOException          if an I/O error occurs
     * @throws ComposerException    if the .config file could not be generated
     * @throws InterruptedException if the current thread is interrupted
     */
    private void generateConfig(@NotNull Map<String, Boolean> features)
            throws IOException, ComposerException, InterruptedException {
        LOGGER.info("Generating .config");
        this.composerStrategy.generateDefConfig();

        writeToExistingConfig(features);

        // Make sure that `include/autoconf.h` (or `build/globalconfig.out`) is generated
        this.composerStrategy.processWrittenConfig();

        verifyConfig(features);
    }

    /**
     * Writes the specified configuration to an existing .config file by replacing the values of the relevant options.
     *
     * @param features the enabled and disabled features
     * @throws IOException if an I/O error occurs
     */
    private void writeToExistingConfig(@NotNull Map<String, Boolean> features) throws IOException {
        // Remove ignored features
        features.keySet().removeIf(feature -> IGNORED_FEATURES_PATTERN.matcher(feature).matches());

        Set<String> remainingFeatures = new HashSet<>(features.keySet());

        Path configPath = this.composerStrategy.getConfigPath();

        Pattern optionNameValuePattern = this.composerStrategy.getOptionNameValuePattern();
        Pattern optionNotSetPattern = this.composerStrategy.getOptionNotSetPattern();

        List<String> defaultConfigLines = Files.readAllLines(configPath, this.encoding);
        defaultConfigLines.replaceAll(line -> {
            String optionName;
            Matcher nameValueMatcher = optionNameValuePattern.matcher(line);
            if (nameValueMatcher.matches()) {
                optionName = nameValueMatcher.group(1);
            } else {
                Matcher notSetMatcher = optionNotSetPattern.matcher(line);
                if (notSetMatcher.matches()) {
                    optionName = notSetMatcher.group(1);
                } else {
                    return line;
                }
            }
            if (features.containsKey(optionName)) {
                remainingFeatures.remove(optionName);
                return this.composerStrategy.formatOption(optionName, features.get(optionName));
            }

            return line;
        });
        for (String remainingFeature : remainingFeatures) {
            defaultConfigLines.add(this.composerStrategy.formatOption(remainingFeature,
                    features.get(remainingFeature)));
        }
        Files.write(configPath, defaultConfigLines, this.encoding);
    }

    /**
     * A sanity check which verifies that the generated .config file contains the expected options. If not, a warning is
     * logged.
     *
     * @param features the enabled and disabled features
     * @throws IOException if an I/O error occurs
     */
    private void verifyConfig(@NotNull Map<String, Boolean> features) throws IOException {
        Path configPath = this.composerStrategy.getConfigPath();
        Pattern optionNameValuePattern = this.composerStrategy.getOptionNameValuePattern();
        Pattern optionNotSetPattern = this.composerStrategy.getOptionNotSetPattern();

        Map<String, Boolean> generatedConfig = Files.readAllLines(configPath, this.encoding).stream()
                .map(line -> {
                    Matcher matcher = optionNameValuePattern.matcher(line);
                    if (matcher.matches()) {
                        return Map.entry(matcher.group(1), true);
                    }
                    matcher = optionNotSetPattern.matcher(line);
                    if (matcher.matches()) {
                        return Map.entry(matcher.group(1), false);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        Map<String, Boolean> modifiedFeatures = features.entrySet().stream()
                .filter(entry -> entry.getValue() != generatedConfig.getOrDefault(entry.getKey(), false))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (!modifiedFeatures.isEmpty()) {
            LOGGER.warn("The following options could not be set in the .config file: {}", modifiedFeatures);
        }
    }

    /**
     * Determines the files that are included in the variant and the compiler flags they are compiled with.
     *
     * @param tmpSourcePath the absolute path to the temporary source directory
     * @return the files that are included in the variant
     * @throws IOException       if an I/O error occurs
     * @throws ComposerException if the files could not be determined
     */
    private @NotNull Set<Dependency> getIncludedFiles(@NotNull Path tmpSourcePath)
            throws IOException, ComposerException, InterruptedException {
        LOGGER.info("Determining files to be included");
        ProcessBuilder makeProcessBuilder = new ProcessBuilder("make", "-in")
                .directory(tmpSourcePath.toFile());
        makeProcessBuilder.environment().put("CC", "gcc");
        makeProcessBuilder.environment().put("LANG", "C");

        int makeExitCode;
        String output;
        LOGGER.debug("Running make -in");
        Process makeProcess = makeProcessBuilder.start();
        try {
            output = IOUtils.toString(makeProcess.getInputStream(), Charset.defaultCharset());
            makeExitCode = makeProcess.waitFor();
        } catch (InterruptedException e) {
            makeProcess.destroy();
            throw e;
        }
        if (makeExitCode != 0)
            throw new ComposerException("make -in failed with exit code %d".formatted(makeExitCode));

        List<GCCCall> gccCalls;
        try {
            LOGGER.debug("Parsing gcc calls");
            gccCalls = new GCCCallExtractor(output).getCalls();
        } catch (ParseException e) {
            throw new ComposerException("gcc calls could not be parsed", e);
        }
        LOGGER.debug("Found {} gcc calls", gccCalls.size());

        List<InclusionInformation> compiledFiles = new ArrayList<>();
        for (GCCCall gccCall : gccCalls) {
            List<InclusionInformation> inclusionInformation = InclusionInformation.fromGCCCall(gccCall, tmpSourcePath);
            compiledFiles.addAll(inclusionInformation);
        }

        return this.getDependencies(compiledFiles, tmpSourcePath);
    }

    /**
     * Determines the dependencies of the specified files.
     *
     * @param compiledFiles the files to determine the dependencies of
     * @param tmpSourcePath the absolute path to the temporary source directory
     * @return the dependencies of the specified files
     * @throws ComposerException    if the dependencies could not be determined
     * @throws IOException          if an I/O error occurs
     * @throws InterruptedException if the current thread is interrupted
     */
    private @NotNull Set<Dependency> getDependencies(@NotNull List<InclusionInformation> compiledFiles,
                                                     @NotNull Path tmpSourcePath)
            throws ComposerException, IOException, InterruptedException {
        LOGGER.info("Getting dependencies");
        Set<Dependency> dependencies = new HashSet<>();
        for (InclusionInformation compiledFile : compiledFiles) {
            dependencies.add(new CompiledDependency(compiledFile));
            dependencies.addAll(this.getDependenciesOfFile(compiledFile, tmpSourcePath));
        }
        LOGGER.debug("Found {} dependencies in total", dependencies.size());
        return dependencies;
    }

    /**
     * Determines the dependencies of the specified file. For non-header files, the compiler flags used for compiling
     * the specified file are included.
     *
     * @param inclusionInformation the file to determine the dependencies of
     * @param tmpSourcePath        the absolute path to the temporary source directory
     * @return the dependencies of the specified file
     * @throws IOException          if an I/O error occurs
     * @throws ComposerException    if the dependencies could not be determined
     * @throws InterruptedException if the current thread is interrupted
     */
    private @NotNull List<Dependency> getDependenciesOfFile(@NotNull InclusionInformation inclusionInformation,
                                                            @NotNull Path tmpSourcePath)
            throws IOException, ComposerException, InterruptedException {
        if (!Files.exists(tmpSourcePath.resolve(inclusionInformation.filePath()))) {
            LOGGER.warn("File {} does not exist, skipping dependency calculation",
                    inclusionInformation.filePath());
            return List.of();
        }
        LOGGER.info("Getting dependencies of {}", inclusionInformation.filePath());

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
                                        .filter(includedFile -> !includedFile.toString().equals(dep))
                                        .collect(Collectors.toSet()),
                                inclusionInformation.defines(),
                                inclusionInformation.includePaths(),
                                inclusionInformation.systemIncludePaths()
                        ));
                    }
                })
                .toList();

        LOGGER.debug("Found {} dependencies", dependencyInformation.size());
        return dependencyInformation;
    }

    /**
     * Calls GCC with the specified compiler flags to determine the dependencies of the specified file. The dependencies
     * use the syntax of a make rule. Dependencies of files generated during a full build may be missed.
     *
     * @param inclusionInformation the file to determine the dependencies of
     * @param tmpSourcePath        the absolute path to the temporary source directory
     * @return the make rule describing the dependencies of the specified file
     * @throws IOException       if an I/O error occurs
     * @throws ComposerException if GCC fails
     */
    private @NotNull String getFileMakeRule(@NotNull InclusionInformation inclusionInformation,
                                            @NotNull Path tmpSourcePath)
            throws IOException, ComposerException, InterruptedException {
        List<String> gccCall = new ArrayList<>();
        gccCall.add("gcc");
        gccCall.addAll(inclusionInformation.includePaths().stream()
                .map(includePath -> "-I" + includePath)
                .toList());
        gccCall.addAll(inclusionInformation.defines().entrySet().stream()
                .map(entry -> "-D" + entry.getKey() + "=" + entry.getValue())
                .toList());
        gccCall.addAll(inclusionInformation.includedFiles().stream()
                .flatMap(includedFile -> Stream.of("-include", includedFile.toString()))
                .toList());
        gccCall.add("-M");
        gccCall.add("-MG");
        gccCall.add(inclusionInformation.filePath().toString());

        ProcessBuilder gccProcessBuilder = new ProcessBuilder(gccCall)
                .directory(tmpSourcePath.toFile());
        Process gccProcess = gccProcessBuilder.start();
        int gccExitCode;
        String output;
        String error;
        try {
            output = IOUtils.toString(gccProcess.getInputStream(), Charset.defaultCharset());
            error = IOUtils.toString(gccProcess.getErrorStream(), Charset.defaultCharset());
            gccExitCode = gccProcess.waitFor();
        } catch (InterruptedException e) {
            gccProcess.destroy();
            throw e;
        }
        if (gccExitCode != 0) {
            LOGGER.error("GCC failed with the following error: {}", error);
            LOGGER.error("Its output was: {}", output);
            LOGGER.error("The command was: {}", String.join(" ", gccCall));
            throw new ComposerException("gcc failed with exit code %d".formatted(gccExitCode));
        }
        return output;
    }

    /**
     * Generates the files required by the specified variant using {@code generateFile}.
     *
     * @param dependencies  the files required by the variant
     * @param destination   the absolute path to the output directory
     * @param tmpSourcePath the absolute path to the temporary source directory
     * @return a map from the paths of the generated files to information about the generation process
     * @throws IOException if an I/O error occurs
     */
    private @NotNull Map<Path, GenerationInformation> generateFiles(@NotNull Set<Dependency> dependencies,
                                                                    @NotNull Path destination,
                                                                    @NotNull Path tmpSourcePath)
            throws IOException {
        LOGGER.info("Generating files");
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
     * @param configurations information about which variants of the file to generate (e.g., with which preprocessor
     *                       directives)
     * @param destination    the absolute path to the output directory
     * @param tmpSourcePath  the absolute path to the temporary source directory
     * @return a map from the relative paths of the generated files to information about the generation process
     * @throws IOException if an I/O error occurs
     */
    private @NotNull Map<Path, GenerationInformation> generateFile(@NotNull Path filePath,
                                                                   @NotNull List<Dependency> configurations,
                                                                   @NotNull Path destination,
                                                                   @NotNull Path tmpSourcePath)
            throws IOException {
        Path sourcePath = tmpSourcePath.resolve(filePath);
        if (!Files.exists(sourcePath)) {
            LOGGER.warn("File {} does not exist, not generating.", filePath);
            return Map.of();
        }
        Path destinationDirectory = destination.resolve(filePath).getParent();
        Files.createDirectories(destinationDirectory);

        Map<Path, GenerationInformation> generationInformation = new HashMap<>();
        boolean copied = false;
        boolean generated = false;
        for (Dependency configuration : configurations) {
            if (configuration instanceof CompiledDependency compiledDependency) {
                Map.Entry<Path, GenerationInformation> fileGenerationInformation
                        = this.generateFileWithPreprocessorDirectives(
                        compiledDependency.getInclusionInformation(),
                        destination,
                        tmpSourcePath
                );
                generated = true;
                generationInformation.put(fileGenerationInformation.getKey(), fileGenerationInformation.getValue());
            } else if (configuration instanceof HeaderDependency) {
                if (!copied) {
                    LOGGER.debug("Copying {}", filePath);
                    Files.copy(sourcePath, destination.resolve(configuration.getComposedFilePath()));
                    copied = true;
                    generationInformation.put(configuration.getComposedFilePath(),
                            new GenerationInformation(filePath, 0));
                }
            }
        }

        if (!generated && !copied) {
            throw new RuntimeException("File %s was neither copied nor generated".formatted(filePath));
        }
        return generationInformation;
    }

    private @NotNull Map.Entry<Path, GenerationInformation> generateFileWithPreprocessorDirectives(
            @NotNull InclusionInformation inclusionInformation, @NotNull Path destination, @NotNull Path tmpSourcePath)
            throws IOException {
        LOGGER.debug("Generating {} with preprocessor directives", inclusionInformation.filePath());
        Path relativeDestinationPath = inclusionInformation.getComposedFilePath();
        Path destinationPath = destination
                .resolve(relativeDestinationPath);

        try (FileOutputStream stream = new FileOutputStream(destinationPath.toFile())) {
            for (Map.Entry<String, String> define : inclusionInformation.defines().entrySet()) {
                stream.write(
                        "#define %s %s%n".formatted(define.getKey(), define.getValue())
                                .getBytes(this.encoding)
                );
            }
            for (Path include : inclusionInformation.includedFiles()) {
                Path includePath = inclusionInformation.filePath().getParent().relativize(include);
                stream.write(("#include \"%s\"%n".formatted(includePath)).getBytes(this.encoding));
            }
            stream.write(Files.readAllBytes(tmpSourcePath.resolve(inclusionInformation.filePath())));
        }

        return Map.entry(relativeDestinationPath, new GenerationInformation(
                inclusionInformation.filePath().normalize(),
                inclusionInformation.defines().size() + inclusionInformation.includedFiles().size()
        ));
    }

    private @NotNull PresenceConditionMapper createPresenceConditionMapper(
            @NotNull Map<Path, GenerationInformation> generationInformation,
            @NotNull Set<Dependency> dependencies,
            @NotNull SourceMap sourceMap,
            @NotNull Set<String> knownFeatures) throws ComposerException, IOException, InterruptedException {
        if (this.shouldSkipPresenceConditionExtraction) {
            LOGGER.info("Skipping presence condition extraction");
            return new EmptyPresenceConditionMapper();
        }

        Map<Path, Dependency> dependenciesByPath = dependencies.stream()
                .collect(Collectors.toMap(Dependency::getComposedFilePath, dependency -> dependency));
        Stream<Map.Entry<Path, InclusionInformation>> inclusionInformation = generationInformation.keySet().stream()
                .map(generatedFilePath -> {
                    Dependency dependency = dependenciesByPath.get(generatedFilePath);
                    if (!(dependency instanceof CompiledDependency)) {
                        return null;
                    }
                    return Map.entry(generatedFilePath, ((CompiledDependency) dependency)
                            .getInclusionInformation());
                })
                .filter(Objects::nonNull)
                .filter(entry -> {
                    Path filePath = entry.getValue().filePath();
                    if (this.presenceConditionExcludes.contains(filePath)) {
                        LOGGER.debug("File {} is excluded from presence condition extraction", filePath);
                        return false;
                    }
                    return true;
                });

        return this.composerStrategy.createPresenceConditionMapper(inclusionInformation, sourceMap, knownFeatures);
    }

    private void copySourceTo(@NotNull Path originalSourcePath, @NotNull Path tmpSourcePath) throws IOException {
        LOGGER.info("Copying source");
        FileUtils.copyDirectory(
                originalSourcePath.toFile(), tmpSourcePath.toFile(),
                file -> !file.getName().equals(".git")
        );
    }

    /**
     * Contains information about a file included in the variant.
     */
    private abstract static class Dependency {
        /**
         * Returns the path to the file, relative to the source directory.
         *
         * @return the path to the file
         */
        public abstract @NotNull Path getFilePath();

        /**
         * Returns the path to the file the composer will generate / has generated for this dependency, relative to the
         * output directory.
         *
         * @return the path to the file
         */
        public abstract @NotNull Path getComposedFilePath();
    }

    /**
     * Contains information about a non-header file included in the variant, specifically the flags used to compile it.
     */
    private static class CompiledDependency extends Dependency {
        private final @NotNull InclusionInformation inclusionInformation;

        public CompiledDependency(@NotNull InclusionInformation inclusionInformation) {
            this.inclusionInformation = inclusionInformation;
        }

        @Override
        public @NotNull Path getFilePath() {
            return this.inclusionInformation.filePath();
        }

        @Override
        public @NotNull Path getComposedFilePath() {
            return this.inclusionInformation.getComposedFilePath();
        }

        public @NotNull InclusionInformation getInclusionInformation() {
            return this.inclusionInformation;
        }

        @Override
        public boolean equals(@Nullable Object o) {
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
        private final @NotNull Path filePath;

        public HeaderDependency(@NotNull String filePath) {
            this.filePath = Path.of(filePath).normalize();
        }

        @Override
        public @NotNull Path getFilePath() {
            return this.filePath;
        }

        @Override
        public @NotNull Path getComposedFilePath() {
            return this.filePath;
        }

        @Override
        public boolean equals(@Nullable Object o) {
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
