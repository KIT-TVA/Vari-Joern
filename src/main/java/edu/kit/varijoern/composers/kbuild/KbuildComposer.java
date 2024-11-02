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
import org.jetbrains.annotations.Nullable;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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
 * A composer for Kconfig-Kbuild-based systems. It copies the files required by the specified variant to the output
 * directory and adds {@code #define} and {@code #include} directives to C files because it is not possible to pass
 * command-line includes and defines to Joern.
 * <p>
 * The composer uses kmax to determine the presence conditions of the individual files
 * (see {@link FilePresenceConditionMapper}). The presence conditions of individual lines are determined using
 * SuperC (see {@link LinePresenceConditionMapper}).
 * <p>
 * Currently, only the Kbuild and Kconfig variants of Linux and Busybox are supported. For Linux, no presence
 * conditions can be determined at the moment.
 * <h2>How it works</h2>
 * <ol>
 *     <li>If not yet done, create a {@link FilePresenceConditionMapper} which determines the presence conditions
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
 *         <li>Run {@code make -in} to determine the files explicitly compiled by {@code make} and the GCC flags they're
 *         compiled with.</li>
 *         <li>Run {@code gcc -M -MG} on each of the files determined in the previous step to determine the files they
 *         depend on. Files that are compiled with different include or define flags are treated as different
 *         dependencies. </li>
 *     </ol>
 *     </li>
 *     </li>
 *     <li>Copy the files determined in the previous step to the output directory and add {@code #define} and
 *     {@code #include} directives to non-header files.</li>
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
 *         <li>See {@link FilePresenceConditionMapper} and {@link LinePresenceConditionMapper} for more information.
 *         </li>
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
    private static final Pattern OPTION_NAME_VALUE_PATTERN_AXTLS = Pattern.compile("(\\w+)=.*");
    private static final Pattern OPTION_NOT_SET_PATTERN = Pattern.compile("# CONFIG_(\\w+) is not set");
    private static final Pattern OPTION_NOT_SET_PATTERN_AXTLS = Pattern.compile("# (\\w+) is not set");
    // Root is the default root feature in feature models extracted by torte. __VISIBILITY__.* features are generated by
    // kmax when it encounters prompts.
    private static final Pattern IGNORED_FEATURES_PATTERN = Pattern.compile("^__VISIBILITY__.*|^Root$");
    private static final Pattern HEADER_FILE_PATTERN = Pattern.compile(".*\\.(?:h|H|hpp|hxx|h++)");
    private static final Set<String> SUPPORTED_SYSTEMS = Set.of("linux", "busybox", "fiasco", "axtls");
    private static final Logger LOGGER = LogManager.getLogger();
    private static final OutputStream STREAM_LOGGER = IoBuilder.forLogger().setLevel(Level.DEBUG).buildOutputStream();

    private final @NotNull String system;
    private final @NotNull Charset encoding;
    private final @NotNull Path tmpPath;
    private final @NotNull Path tmpSourcePath;
    private final @NotNull Set<Path> presenceConditionExcludes;
    private final boolean shouldSkipPresenceConditionExtraction;
    private @Nullable FilePresenceConditionMapper filePresenceConditionMapper = null;

    /**
     * Creates a new {@link KbuildComposer} which will create variants from the specified source directory.
     *
     * @param sourcePath                the path to the source directory. Must be an absolute path.
     * @param system                    the variant of the Kbuild/Kconfig system. Use
     *                                  {@link KbuildComposer#isSupportedSystem(String)}
     *                                  to determine if a given system is supported.
     * @param encoding                  the encoding of the source files
     * @param tmpPath                   a {@link Path} to a temporary directory that can be used by the composer. Must
     *                                  be absolute.
     * @param presenceConditionExcludes a list of paths to exclude from presence condition extraction. Must be relative
     *                                  to the source directory.
     * @throws IOException          if an I/O error occurs
     * @throws ComposerException    if the composer could not be created for another reason
     * @throws InterruptedException if the current thread is interrupted
     */
    public KbuildComposer(@NotNull Path sourcePath, @NotNull String system, @NotNull Charset encoding,
                          @NotNull Path tmpPath, @NotNull Set<Path> presenceConditionExcludes,
                          boolean shouldSkipPresenceConditionExtraction)
            throws IOException, ComposerException, InterruptedException {
        this.system = system;
        this.encoding = encoding;
        this.tmpPath = tmpPath;
        this.presenceConditionExcludes = presenceConditionExcludes
                .stream()
                .map(Path::normalize)
                .collect(Collectors.toSet());
        this.shouldSkipPresenceConditionExtraction = shouldSkipPresenceConditionExtraction;
        this.tmpSourcePath = this.tmpPath.resolve("source");
        this.copySourceTo(sourcePath, this.tmpSourcePath);
        // Make sure that there are no compilation artifacts.
        // These would break dependency detection because make would not try to recompile them.
        switch (this.system) {
            case "linux", "busybox" -> this.runMake("distclean");
            case "fiasco" -> {
                // build/source is usually a symlink. During copying, it is converted to a directory. `make purge`
                // assumes that it can delete it non-recursively with `rm`, which fails. We have to delete it manually.
                FileUtils.deleteDirectory(this.tmpSourcePath.resolve("build/source").toFile());
                this.runMake("purge");
            }
            case "axtls" -> {
                this.runMake("-i", "clean");
                axtlsCleanConf();
            }
            default -> throw new IllegalStateException();
        }
        if (this.system.equals("busybox")) {
            // BusyBox's Kbuild variant allows to specify Kbuild information in the source files. Since kmax cannot
            // handle this, we use `make gen_build_files` to generate Kbuild files.
            this.runMake("gen_build_files");
        }
    }

    private void axtlsCleanConf() throws ComposerException, IOException, InterruptedException {
        this.runMake("-i", "cleanconf");
        // axtls's cleanconf command should delete config/config.h, but due to a bug in a Makefile, it doesn't.
        // We have to delete it manually.
        Files.deleteIfExists(this.tmpSourcePath.resolve("config/config.h"));
    }

    @Override
    public @NotNull CompositionInformation compose(@NotNull Map<String, Boolean> features, @NotNull Path destination,
                                                   @NotNull IFeatureModel featureModel)
            throws IOException, ComposerException, InterruptedException {
        if (this.filePresenceConditionMapper == null) {
            if (this.shouldSkipPresenceConditionExtraction) {
                LOGGER.info("Skipping file presence condition extraction");
                this.filePresenceConditionMapper = new FilePresenceConditionMapper();
            } else {
                LOGGER.info("Creating file presence condition mapper");
                this.filePresenceConditionMapper = new FilePresenceConditionMapper(this.tmpSourcePath, this.system,
                        this.tmpPath, featureModel);
            }
        }

        this.generateConfig(features, tmpSourcePath);

        if (this.system.equals("busybox")) {
            // We have to run `make applets` to generate several header files.
            this.runMake("applets");
            // `make applets` generates `applets/applets.o`, which is part of the final BusyBox executables and would
            // break dependency detection. We have to delete it.
            Files.delete(tmpSourcePath.resolve("applets/applets.o"));
        }
        Set<Dependency> includedFiles = this.getIncludedFiles(tmpSourcePath);
        Map<Path, GenerationInformation> generationInformation = this.generateFiles(
                includedFiles, destination, tmpSourcePath
        );
        Map<Path, LinePresenceConditionMapper> linePresenceConditionMappers = this.createLinePresenceConditionMappers(
                generationInformation,
                includedFiles,
                featureModel.getFeatures().stream()
                        .map(IFeatureModelElement::getName)
                        .collect(Collectors.toSet())
        );
        return new CompositionInformation(
                destination,
                features,
                new KbuildPresenceConditionMapper(this.filePresenceConditionMapper, linePresenceConditionMappers,
                        generationInformation),
                new KbuildComposerSourceMap(generationInformation, tmpSourcePath),
                List.of(new CCPPLanguageInformation(
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
                ))
        );
    }

    /**
     * Generates a .config file based on the specified features and ensures that the {@code include/autoconf.h} file
     * exists and is up-to-date.
     *
     * @param features      the enabled and disabled features
     * @param tmpSourcePath the absolute path to the temporary source directory
     * @throws IOException          if an I/O error occurs
     * @throws ComposerException    if the .config file could not be generated
     * @throws InterruptedException if the current thread is interrupted
     */
    private void generateConfig(@NotNull Map<String, Boolean> features, @NotNull Path tmpSourcePath)
            throws IOException, ComposerException, InterruptedException {
        LOGGER.info("Generating .config");
        switch (this.system) {
            case "linux", "busybox" -> this.runMake("defconfig");
            case "fiasco" -> {
                // Fiasco does not have a `defconfig` command. We have to clean the build directory and run
                // `olddefconfig` instead.
                Files.deleteIfExists(tmpSourcePath.resolve("build/globalconfig.out"));
                this.runMake("olddefconfig");
            }
            case "axtls" -> this.runMake("allyesconfig");
            default -> throw new IllegalStateException();
        }

        // Remove ignored features
        features.keySet().removeIf(feature -> IGNORED_FEATURES_PATTERN.matcher(feature).matches());

        Set<String> remainingFeatures = new HashSet<>(features.keySet());
        Path configPath = switch (this.system) {
            case "linux", "busybox" -> tmpSourcePath.resolve(".config");
            case "fiasco" -> tmpSourcePath.resolve("build/globalconfig.out");
            case "axtls" -> tmpSourcePath.resolve("config/.config");
            default -> throw new IllegalStateException();
        };
        List<String> defaultConfigLines = Files.readAllLines(configPath, this.encoding);
        Pattern optionNameValuePattern = this.system.equals("axtls")
                ? OPTION_NAME_VALUE_PATTERN_AXTLS
                : OPTION_NAME_VALUE_PATTERN;
        Pattern optionNotSetPattern = this.system.equals("axtls")
                ? OPTION_NOT_SET_PATTERN_AXTLS
                : OPTION_NOT_SET_PATTERN;
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
                return formatOption(optionName, features.get(optionName));
            }

            return line;
        });
        for (String remainingFeature : remainingFeatures) {
            defaultConfigLines.add(formatOption(remainingFeature, features.get(remainingFeature)));
        }
        Files.write(configPath, defaultConfigLines, this.encoding);

        // Make sure that `include/autoconf.h` (or `build/globalconfig.out`) is generated
        switch (this.system) {
            case "linux", "busybox", "axtls" -> this.runMake("oldconfig");
            case "fiasco" -> this.runMake("olddefconfig");
            default -> throw new IllegalStateException();
        }

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
     * Creates a line for a .config file containing the specified option.
     *
     * @param optionName the name of the option
     * @param activated  whether the option is activated
     * @return the line that can be used in a .config file
     */
    private @NotNull String formatOption(@NotNull String optionName, boolean activated) {
        if (activated) {
            if (this.system.equals("axtls")) {
                return "%s=y".formatted(optionName);
            }
            return "CONFIG_%s=y".formatted(optionName);
        } else {
            if (this.system.equals("axtls")) {
                return "# %s is not set".formatted(optionName);
            }
            return "# CONFIG_%s is not set".formatted(optionName);
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
        if (this.system.equals("fiasco")) {
            // Fiasco uses a second preprocessor for its C++ files. We have to call it before we determine the
            // dependencies without running actual commands.
            LOGGER.info("Running fiasco's C++ preprocessor");
            this.runMake("-C", "build", "create-sources");
        }
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
     * using the syntax of a make rule. Dependencies of files generated during a full build may be missed.
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
     * @param configurations information about which variants of the file to generate (e.g. with which preprocessor
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
            if (configuration instanceof CompiledDependency) {
                Map.Entry<Path, GenerationInformation> fileGenerationInformation
                        = this.generateFileWithPreprocessorDirectives(
                        ((CompiledDependency) configuration).getInclusionInformation(),
                        destination,
                        tmpSourcePath
                );
                generationInformation.put(fileGenerationInformation.getKey(), fileGenerationInformation.getValue());
                generated = true;
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

    private @NotNull Map<Path, LinePresenceConditionMapper> createLinePresenceConditionMappers(
            @NotNull Map<Path, GenerationInformation> generationInformation,
            @NotNull Set<Dependency> dependencies,
            @NotNull Set<String> knownFeatures)
            throws IOException, ComposerException, InterruptedException {
        if (this.shouldSkipPresenceConditionExtraction) {
            LOGGER.info("Skipping line presence condition extraction");
            return Map.of();
        }
        if (!LinePresenceConditionMapper.isSupportedSystem(this.system)) {
            LOGGER.warn("System {} is not supported, skipping line presence condition mapper creation",
                    this.system);
            return Map.of();
        }

        LOGGER.info("Creating line presence condition mappers");

        // Clean up to ensure that the header file containing config definitions doesn't exist
        this.runMake("clean");
        if (this.system.equals("axtls")) {
            this.axtlsCleanConf();
        }

        Map<Path, LinePresenceConditionMapper> linePresenceConditionMappers = new HashMap<>();
        Map<Path, Dependency> dependenciesByPath = dependencies.stream()
                .collect(Collectors.toMap(Dependency::getComposedFilePath, dependency -> dependency));
        for (Map.Entry<Path, GenerationInformation> entry : generationInformation.entrySet()) {
            Path generatedFilePath = entry.getKey();
            GenerationInformation fileGenerationInformation = entry.getValue();
            Dependency dependency = dependenciesByPath.get(generatedFilePath);
            if (!(dependency instanceof CompiledDependency)) {
                continue;
            }
            InclusionInformation inclusionInformation = ((CompiledDependency) dependency).getInclusionInformation();
            if (this.presenceConditionExcludes.contains(inclusionInformation.filePath())) {
                LOGGER.debug("File {} has been excluded from presence condition extraction",
                        inclusionInformation.filePath());
                continue;
            }
            LOGGER.debug("Creating line presence condition mapper for {}", entry.getKey());
            linePresenceConditionMappers.put(
                    generatedFilePath,
                    new LinePresenceConditionMapper(inclusionInformation, this.tmpSourcePath,
                            fileGenerationInformation.addedLines(), knownFeatures, this.system, this.encoding)
            );
        }

        return linePresenceConditionMappers;
    }

    /**
     * A helper function to run make with the specified arguments with the temporary source directory as the working
     * directory.
     *
     * @param args the arguments to pass to make
     * @throws ComposerException    if make returns a non-zero exit code
     * @throws IOException          if an I/O error occurs
     * @throws InterruptedException if the current thread is interrupted
     */
    private void runMake(String @NotNull ... args) throws ComposerException, IOException, InterruptedException {
        Process makeProcess = new ProcessBuilder(
                Stream.concat(Stream.of("make"), Arrays.stream(args))
                        .toList())
                .directory(this.tmpSourcePath.toFile())
                .start();
        StreamGobbler stdoutGobbler = new StreamGobbler(makeProcess.getInputStream(), STREAM_LOGGER);
        StreamGobbler stderrGobbler = new StreamGobbler(makeProcess.getErrorStream(), STREAM_LOGGER);
        stdoutGobbler.start();
        stderrGobbler.start();
        int makeExitCode;
        try {
            makeExitCode = makeProcess.waitFor();
        } catch (InterruptedException e) {
            makeProcess.destroy();
            throw e;
        }
        if (makeExitCode != 0)
            throw new ComposerException("Make failed with exit code %d".formatted(makeExitCode));
    }

    private void copySourceTo(@NotNull Path originalSourcePath, @NotNull Path tmpSourcePath) throws IOException {
        LOGGER.info("Copying source");
        FileUtils.copyDirectory(
                originalSourcePath.toFile(), tmpSourcePath.toFile(),
                file -> !file.getName().equals(".git")
        );
    }

    /**
     * Determines whether the specified variant of Kbuild/Kconfig is supported.
     *
     * @param system the variant to check
     * @return if the variant is supported
     */
    public static boolean isSupportedSystem(@NotNull String system) {
        return SUPPORTED_SYSTEMS.contains(system);
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
