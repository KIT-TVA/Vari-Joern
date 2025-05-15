package edu.kit.varijoern.composers.kconfig;

import edu.kit.varijoern.ConditionUtils;
import edu.kit.varijoern.KconfigTestCaseManager;
import edu.kit.varijoern.KconfigTestCasePreparer;
import edu.kit.varijoern.PresenceConditionExpectation;
import edu.kit.varijoern.composers.*;
import edu.kit.varijoern.composers.conditionmapping.PresenceConditionMapper;
import edu.kit.varijoern.composers.kconfig.subjects.*;
import edu.kit.varijoern.composers.sourcemap.SourceLocation;
import edu.kit.varijoern.composers.sourcemap.SourceMap;
import edu.kit.varijoern.featuremodel.FeatureModelReaderException;
import edu.kit.varijoern.samplers.FixedSampler;
import edu.kit.varijoern.samplers.SamplerException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.prop4j.Node;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class KconfigComposerTest {
    private static final List<KconfigTestCasePreparer> STANDARD_PREPARERS = List.of(
            (path) -> {
            },
            KconfigComposerTest::buildAllYesConfig
    );

    private static Stream<Arguments> testCases() {
        return Stream.of(busyboxTestCases(), linuxTestCases(), fiascoTestCases(), axtlsTestCases())
                .flatMap(s -> s);
    }

    private static Stream<Arguments> busyboxTestCases() {
        Set<Path> standardIncludedFiles = Set.of(Path.of("include/autoconf.h"),
                Path.of("include/cmdline-included-header.h"));
        List<Path> standardIncludePaths = List.of(Path.of("include"));
        List<Path> standardSystemIncludePaths = List.of();
        InclusionInformation mainC = new InclusionInformation(
                Path.of("src/main.c"),
                standardIncludedFiles,
                standardBusyboxDefinesForFile("main"),
                standardIncludePaths,
                standardSystemIncludePaths
        );
        Path noPresenceConditionCPath = Path.of("src/no-presence-condition.c");
        InclusionInformation noPresenceConditionC = new InclusionInformation(
                noPresenceConditionCPath,
                standardIncludedFiles,
                standardBusyboxDefinesForFile("no_presence_condition"),
                standardIncludePaths,
                standardSystemIncludePaths
        );
        InclusionInformation includedCByMain = new InclusionInformation(
                Path.of("src/included.c"),
                standardIncludedFiles,
                standardBusyboxDefinesForFile("main"),
                standardIncludePaths,
                standardSystemIncludePaths
        );
        InclusionInformation includedCByIO = new InclusionInformation(
                Path.of("src/included.c"),
                standardIncludedFiles,
                standardBusyboxDefinesForFile("io_file"),
                standardIncludePaths,
                standardSystemIncludePaths
        );
        InclusionInformation appletsC = new InclusionInformation(
                Path.of("applets/applets.c"),
                standardIncludedFiles,
                standardBusyboxDefinesForFile("applets"),
                standardIncludePaths,
                standardSystemIncludePaths
        );
        Stream<TestCase> testCases = Stream.of(
                new TestCase(
                        "busybox-sample",
                        "busybox",
                        new BusyboxStrategyFactory(),
                        List.of(),
                        List.of(
                                new FileAbsentVerifier(".*\\.src"),
                                new FileAbsentVerifier(".*\\.in"),
                                new FileAbsentVerifier(".*\\.o"),
                                FileAbsentVerifier.originalSourceAndHeader("src/hello-cpp"),
                                FileAbsentVerifier.originalSourceAndHeader("src/io-file"),
                                new FileContentVerifier(mainC),
                                new FileContentVerifier(noPresenceConditionC),
                                new FileContentVerifier(includedCByMain),
                                new FileContentVerifier(appletsC),
                                new FileContentVerifier(Path.of("src/main.h")),
                                new FileContentVerifier(Path.of("include/cmdline-included-header.h"))
                        ),
                        Set.of(noPresenceConditionCPath)
                ),
                new TestCase(
                        "busybox-sample",
                        "busybox",
                        new BusyboxStrategyFactory(),
                        List.of(
                                "INCLUDE_IO_FILE",
                                "PERFORM_RENAME",
                                "PERFORM_CHMOD",
                                "USE_GETS",
                                "USE_CPP_FILE"
                        ),
                        List.of(
                                new FileAbsentVerifier(".*\\.src"),
                                new FileAbsentVerifier(".*\\.in"),
                                new FileAbsentVerifier(".*\\.o"),
                                new FileContentVerifier(new InclusionInformation(
                                        Path.of("src/hello-cpp.cc"),
                                        standardIncludedFiles,
                                        standardBusyboxDefinesForFile("hello_cpp"),
                                        standardIncludePaths,
                                        standardSystemIncludePaths
                                )),
                                new FileContentVerifier(Path.of("src/hello-cpp.h")),
                                new FileContentVerifier(new InclusionInformation(
                                        Path.of("src/io-file.c"),
                                        standardIncludedFiles,
                                        standardBusyboxDefinesForFile("io_file"),
                                        standardIncludePaths,
                                        standardSystemIncludePaths
                                )),
                                new FileContentVerifier(includedCByIO),
                                new FileContentVerifier(Path.of("src/io-file.h")),
                                new FileContentVerifier(mainC),
                                new FileContentVerifier(noPresenceConditionC),
                                new FileContentVerifier(includedCByMain),
                                new FileContentVerifier(appletsC),
                                new FileContentVerifier(Path.of("src/main.h")),
                                new FileContentVerifier(Path.of("include/cmdline-included-header.h"))
                        ),
                        Set.of(noPresenceConditionCPath)
                )
        );
        return testCases.flatMap(
                testCase -> STANDARD_PREPARERS.stream()
                        .map(preparer -> Arguments.of(testCase, preparer))
        );
    }

    private static Stream<Arguments> linuxTestCases() {
        Set<Path> standardIncludedFiles = Stream.of("include/linux/compiler-version.h", "include/linux/kconfig.h",
                "include/linux/compiler_types.h").map(Path::of).collect(Collectors.toSet());
        List<Path> standardIncludePaths = Stream.of("arch/x86/include", "arch/x86/include/generated",
                "include", "arch/x86/include/uapi", "arch/x86/include/generated/uapi",
                "include/uapi", "include/generated/uapi").map(Path::of).toList();
        List<Path> standardSystemIncludePaths = List.of();
        InclusionInformation mainC = new InclusionInformation(
                Path.of("src/main.c"),
                standardIncludedFiles,
                standardLinuxDefinesForFile("main"),
                standardIncludePaths,
                standardSystemIncludePaths
        );
        Stream<TestCase> testCases = Stream.of(
                new TestCase(
                        "linux-sample",
                        "linux",
                        new LinuxStrategyFactory(),
                        List.of(),
                        List.of(
                                new FileAbsentVerifier(".*\\.o"),
                                FileAbsentVerifier.originalSourceAndHeader("src/io-file"),
                                new FileContentVerifier(mainC),
                                new FileContentVerifier(Path.of("src/main.h"))
                        )
                ),
                new TestCase(
                        "linux-sample",
                        "linux",
                        new LinuxStrategyFactory(),
                        List.of(
                                "INCLUDE_IO_FILE",
                                "PERFORM_RENAME",
                                "PERFORM_CHMOD",
                                "USE_GETS"
                        ),
                        List.of(
                                new FileAbsentVerifier(".*\\.o"),
                                new FileContentVerifier(new InclusionInformation(
                                        Path.of("src/io-file.c"),
                                        standardIncludedFiles,
                                        standardLinuxDefinesForFile("io-file"),
                                        standardIncludePaths,
                                        standardSystemIncludePaths
                                )),
                                new FileContentVerifier(Path.of("src/io-file.h")),
                                new FileContentVerifier(mainC),
                                new FileContentVerifier(Path.of("src/main.h"))
                        )
                )
        );
        return testCases.flatMap(
                testCase -> STANDARD_PREPARERS.stream()
                        .map(preparer -> Arguments.of(testCase, preparer))
        );
    }

    private static Stream<Arguments> fiascoTestCases() {
        List<Path> standardSystemIncludePaths = List.of(getGCCIncludePath());
        List<Path> standardIncludePaths = List.of(Path.of("build"), Path.of("build/auto"));
        Verifier mainVerifier = new FiascoFileContentVerifier(new InclusionInformation(
                Path.of("src/sample/main.cpp"),
                Set.of(),
                Map.of(),
                standardIncludePaths,
                standardSystemIncludePaths
        )); // The build/* files aren't in the original source, so they must be read from the
        // composer's tmp dir, which will run fiasco's preprocessor to generate them
        Stream<TestCase> testCases = Stream.of(
                new TestCase(
                        "fiasco-sample",
                        "fiasco",
                        new FiascoStrategyFactory(),
                        List.of(
                                "AMD64",
                                "__VISIBILITY__CONFIG_CC",
                                "__VISIBILITY__CONFIG_CXX",
                                "__VISIBILITY__CONFIG_LD",
                                "__VISIBILITY__CONFIG_HOST_CC",
                                "__VISIBILITY__CONFIG_HOST_CXX",
                                "DEFINE_USELESS_FUNCTION"
                        ),
                        List.of(
                                new FileAbsentVerifier(".*\\.o"),
                                FileAbsentVerifier.fiascoPreprocessorArtifacts("io_file"),
                                mainVerifier
                        )
                ),
                new TestCase(
                        "fiasco-sample",
                        "fiasco",
                        new FiascoStrategyFactory(),
                        List.of(
                                "INCLUDE_IO_FILE",
                                "PERFORM_RENAME",
                                "PERFORM_CHMOD",
                                "USE_GETS",
                                "DEFINE_USELESS_FUNCTION",
                                "AMD64",
                                "__VISIBILITY__CONFIG_CC",
                                "__VISIBILITY__CONFIG_CXX",
                                "__VISIBILITY__CONFIG_LD",
                                "__VISIBILITY__CONFIG_HOST_CC",
                                "__VISIBILITY__CONFIG_HOST_CXX"
                        ),
                        List.of(
                                new FileAbsentVerifier(".*\\.o"),
                                new FiascoFileContentVerifier(new InclusionInformation(
                                        Path.of("src/sample/io_file.cpp"),
                                        Set.of(),
                                        Map.of(),
                                        standardIncludePaths,
                                        standardSystemIncludePaths
                                )),
                                mainVerifier
                        )
                )
        );

        // This test case does not set DEFINE_USELESS_FUNCTION, in order to test that the composer generates correct
        // source maps even when fiasco's preprocessor drops some lines
        TestCase noUselessFunction = new TestCase(
                "fiasco-sample",
                "fiasco",
                new FiascoStrategyFactory(),
                List.of(
                        "AMD64",
                        "__VISIBILITY__CONFIG_CC",
                        "__VISIBILITY__CONFIG_CXX",
                        "__VISIBILITY__CONFIG_LD",
                        "__VISIBILITY__CONFIG_HOST_CC",
                        "__VISIBILITY__CONFIG_HOST_CXX"
                ),
                List.of(
                        new FileAbsentVerifier(".*\\.o"),
                        FileAbsentVerifier.fiascoPreprocessorArtifacts("io_file"),
                        mainVerifier
                )
        );

        return Stream.concat(
                testCases.flatMap(
                        testCase -> STANDARD_PREPARERS.stream()
                                .map(preparer -> Arguments.of(testCase, preparer))
                ),
                Stream.of(Arguments.of(
                        noUselessFunction,
                        (KconfigTestCasePreparer) ((path) -> {
                        })
                ))
        );
    }

    private static Stream<Arguments> axtlsTestCases() {
        Set<Path> standardIncludedFiles = Set.of();
        List<Path> standardIncludePaths = List.of(Path.of("config"), Path.of("ssl"), Path.of("crypto"));
        List<Path> standardSystemIncludePaths = List.of();
        Map<String, String> standardDefines = Map.of();
        InclusionInformation mainC = new InclusionInformation(
                Path.of("src/main.c"),
                standardIncludedFiles,
                standardDefines,
                standardIncludePaths,
                standardSystemIncludePaths
        );
        Stream<TestCase> testCases = Stream.of(
                new TestCase(
                        "axtls-sample",
                        "axtls",
                        new AxtlsStrategyFactory(),
                        List.of("HAVE_DOT_CONFIG", "CONFIG_PLATFORM_LINUX", "__VISIBILITY__CONFIG_PREFIX"),
                        List.of(
                                new FileAbsentVerifier(".*\\.o"),
                                FileAbsentVerifier.originalSourceAndHeader("src/io-file"),
                                new FileContentVerifier(mainC),
                                new FileContentVerifier(Path.of("src/main.h"))
                        )
                ),
                new TestCase(
                        "axtls-sample",
                        "axtls",
                        new AxtlsStrategyFactory(),
                        List.of(
                                "HAVE_DOT_CONFIG",
                                "CONFIG_PLATFORM_LINUX",
                                "__VISIBILITY__CONFIG_PREFIX",
                                "CONFIG_INCLUDE_IO_FILE",
                                "CONFIG_PERFORM_RENAME",
                                "CONFIG_PERFORM_CHMOD",
                                "CONFIG_USE_GETS"
                        ),
                        List.of(
                                new FileAbsentVerifier(".*\\.o"),
                                new FileContentVerifier(new InclusionInformation(
                                        Path.of("src/io-file.c"),
                                        standardIncludedFiles,
                                        standardDefines,
                                        standardIncludePaths,
                                        standardSystemIncludePaths
                                )),
                                new FileContentVerifier(Path.of("src/io-file.h")),
                                new FileContentVerifier(mainC),
                                new FileContentVerifier(Path.of("src/main.h"))
                        )
                )
        );
        return testCases.flatMap(
                testCase -> STANDARD_PREPARERS.stream()
                        .map(preparer -> Arguments.of(testCase, preparer))
        );
    }

    private static Path getGCCIncludePath() {
        try {
            return Path.of(new String(
                    new ProcessBuilder("gcc", "-print-file-name=include")
                            .start()
                            .getInputStream()
                            .readAllBytes()
            ).trim());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void buildAllYesConfig(Path sourcePath) throws IOException {
        runMake(sourcePath, "allyesconfig");
        runMake(sourcePath);
    }

    private static void runMake(Path sourcePath, String... args) throws IOException {
        ProcessBuilder makeProcessBuilder = new ProcessBuilder(
                Stream.concat(Stream.of("make"), Arrays.stream(args))
                        .toList())
                .directory(sourcePath.toFile());
        Process makeProcess = makeProcessBuilder.start();
        int makeExitCode;
        try {
            makeExitCode = makeProcess.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (makeExitCode != 0) {
            throw new RuntimeException("make failed with exit code " + makeExitCode);
        }
    }

    @NotNull
    private static Map<String, String> standardBusyboxDefinesForFile(String baseName) {
        return Map.of(
                "_GNU_SOURCE", "",
                "NDEBUG", "",
                "BB_VER", "\"1.37.0.git\"",
                "KBUILD_BASENAME", "\"%s\"".formatted(baseName),
                "KBUILD_MODNAME", "\"%s\"".formatted(baseName)
        );
    }

    /**
     * Returns the command-line defines for a file in the `src` directory as used by the Linux variant of Kbuild.
     *
     * @param baseName the name of the file, without the extension and the directory
     * @return the command-line defines
     */
    @NotNull
    private static Map<String, String> standardLinuxDefinesForFile(String baseName) {
        String modName = baseName.replace('-', '_');
        return Map.of(
                "__KERNEL__", "",
                "KBUILD_MODFILE", "\"src/%s\"".formatted(baseName),
                "KBUILD_BASENAME", "\"%s\"".formatted(modName),
                "KBUILD_MODNAME", "\"%s\"".formatted(modName),
                "__KBUILD_MODNAME", "kmod_%s".formatted(modName)
        );
    }

    @ParameterizedTest
    @MethodSource("testCases")
    void runTestCase(TestCase testCase, KconfigTestCasePreparer preparer, @TempDir Path tempDir)
            throws IOException, GitAPIException, InterruptedException, FeatureModelReaderException {
        KconfigTestCaseManager testCaseManager = new KconfigTestCaseManager(testCase.name, preparer);
        Map<String, Boolean> featureMap;
        try {
            featureMap = new FixedSampler(List.of(testCase.enabledFeatures), testCaseManager.getCorrectFeatureModel())
                    .sample(null, tempDir.resolve("sampler"))
                    .get(0);
        } catch (SamplerException e) {
            throw new RuntimeException(e);
        }

        Path originalDirectory = testCaseManager.getPath();
        Path destinationBaseDirectory = Files.createTempDirectory("vari-joern-test-kconfig-composer");
        Path destinationDirectory = Files.createDirectory(destinationBaseDirectory.resolve("composition"));
        Path composerTmpDirectory = Files.createDirectory(destinationBaseDirectory.resolve("tmp"));

        System.err.println("Created temporary directory " + destinationBaseDirectory);

        CompositionInformation compositionInformation;
        try {
            Composer composer = new KconfigComposer(testCaseManager.getPath(),
                    testCase.composerStrategyFactory, Charset.forName(testCaseManager.getMetadata().encoding()),
                    composerTmpDirectory, testCase.presenceConditionExcludes, false);
            compositionInformation = composer.compose(featureMap,
                    destinationDirectory,
                    testCaseManager.getCorrectFeatureModel()
            );
        } catch (ComposerException e) {
            throw new RuntimeException(e);
        }

        for (Verifier verifier : testCase.verifiers) {
            verifier.verify(compositionInformation, testCaseManager, originalDirectory, destinationDirectory,
                    composerTmpDirectory);
        }

        assertTrue(testCaseManager.getModifications().isEmpty(), "Composer modified original source");
    }

    /**
     * A test case for the {@link KconfigComposer}.
     *
     * @param name                      the name of the test case
     * @param system                    the Kconfig/Kbuild implementation of the test case
     * @param composerStrategyFactory   a {@link ComposerStrategyFactory} for the Kconfig/Kbuild implementation
     * @param enabledFeatures           the features to enable for the test case
     * @param verifiers                 the verifiers to run on the composition
     * @param presenceConditionExcludes the paths to exclude from presence condition determination
     */
    private record TestCase(String name, String system, ComposerStrategyFactory composerStrategyFactory,
                            List<String> enabledFeatures, List<Verifier> verifiers,
                            Set<Path> presenceConditionExcludes) {
        /**
         * Creates a new test case for the {@link KconfigComposer}.
         *
         * @param name                    the name of the test case
         * @param system                  the Kconfig/Kbuild implementation of the test case
         * @param composerStrategyFactory a {@link ComposerStrategyFactory} for the Kconfig/Kbuild implementation
         * @param enabledFeatures         the features to enable for the test case
         * @param verifiers               the verifiers to run on the composition
         */
        public TestCase(String name, String system, ComposerStrategyFactory composerStrategyFactory,
                        List<String> enabledFeatures, List<Verifier> verifiers) {
            this(name, system, composerStrategyFactory, enabledFeatures, verifiers, Set.of());
        }
    }

    /**
     * A verifier for a composition. Each verifier checks a specific aspect of the composition and throws an exception
     * if the aspect is not as expected.
     */
    private interface Verifier {
        void verify(CompositionInformation compositionInformation, KconfigTestCaseManager testCaseManager,
                    Path originalDirectory, Path compositionDirectory, Path composerTmpDirectory)
                throws IOException;
    }

    /**
     * A verifier that checks that there are no files matching a regular expression in the composition.
     */
    private static class FileAbsentVerifier implements Verifier {
        private final Pattern regex;

        public FileAbsentVerifier(String regex) {
            this.regex = Pattern.compile(regex);
        }

        /**
         * Returns a verifier that checks that there are no files related to the specified source file in the
         * composition. Forbidden files are:
         * <ul>
         *     <li>{@code name.c}</li>
         *     <li>{@code name.cc}</li>
         *     <li>{@code name.h}</li>
         * </ul>
         * where {@code name} is the specified name. To make sure that the verifier also matches the file names
         * generated by the {@link KconfigComposer}, the applied regular expression is {@code name[-0-9]*\.(cc|c|h)}.
         *
         * @param name the file to check for, without the extension
         * @return the verifier
         */
        public static Verifier originalSourceAndHeader(String name) {
            return new FileAbsentVerifier(name + "[-0-9]*\\.(cc|c|h)");
        }

        /**
         * Returns a verifier that checks that there are no files generated by fiasco's C++ preprocessor.
         * Forbidden files are:
         * <ul>
         *     <li>{@code build/auto/name.cc}</li>
         *     <li>{@code build/auto/name.h}</li>
         *     <li>{@code build/auto/name_i.h}</li>
         * </ul>
         * where {@code name} is the specified name. To make sure that the verifier also matches the file names
         * generated by the {@link KconfigComposer}, the applied regular expression is
         * {@code name[-0-9]*\.(cc|h)|_i[-0-9]*\.h}.
         *
         * @param name the file to check for, without the extension
         * @return the verifier
         */
        public static Verifier fiascoPreprocessorArtifacts(String name) {
            return new FileAbsentVerifier(name + "[-0-9]*\\.(cc|h)|_i[-0-9]*\\.h");
        }

        @Override
        public void verify(CompositionInformation compositionInformation, KconfigTestCaseManager testCaseManager,
                           Path originalDirectory, Path compositionDirectory, Path composerTmpDirectory)
                throws IOException {
            try (var stream = Files.walk(compositionDirectory)) {
                assertNull(stream.filter(path -> regex.matcher(
                                        compositionDirectory.relativize(path).normalize().toString()
                                ).matches())
                                .findAny()
                                .orElse(null),
                        "File matching " + regex + " should not exist"
                );
            }
        }
    }

    /**
     * A verifier that checks that a file in the composition is present and has the expected content. This means that
     * the original file's content is preserved and the correct preprocessor directives are prepended. It also verifies
     * the presence conditions determined by the composer.
     */
    private static class FileContentVerifier implements Verifier {
        private final Path originalRelativePath;
        private final Path composedRelativePath;
        private final List<String> expectedPrependedLines;
        private final List<Path> expectedIncludePaths;
        private final List<Path> expectedSystemIncludePaths;
        private final boolean useComposerTmpDir;

        /**
         * Creates a new verifier for a file in the composition. This constructor assumes that the file is not renamed
         * by the composer and that no prepended preprocessor directives are expected.
         *
         * @param relativePath the relative path to the file
         */
        public FileContentVerifier(Path relativePath) {
            this(relativePath, relativePath, List.of(), null, null);
        }

        /**
         * Creates a new verifier for a file in the composition. This constructor assumes that the file is not renamed
         * by the composer and that no prepended preprocessor directives are expected.
         *
         * @param relativePath      the relative path to the file
         * @param useComposerTmpDir whether to read the original file from the composer's temporary directory
         */
        public FileContentVerifier(Path relativePath, boolean useComposerTmpDir) {
            this(relativePath, relativePath, List.of(), null, null, useComposerTmpDir);
        }

        /**
         * Creates a new verifier for a file in the composition with the specified paths to the original file name and
         * the file name generated by the composer. The verifier checks that the file has the expected preprocessor
         * directives prepended.
         *
         * @param originalRelativePath       the relative path to the original file
         * @param composedRelativePath       the relative path to the file in the composition
         * @param expectedPrependedLines     the expected preprocessor directives
         * @param expectedIncludePaths       the expected include paths for this file
         * @param expectedSystemIncludePaths the expected system include paths for this file
         */
        public FileContentVerifier(Path originalRelativePath, Path composedRelativePath,
                                   List<String> expectedPrependedLines, List<Path> expectedIncludePaths,
                                   List<Path> expectedSystemIncludePaths) {
            this(originalRelativePath, composedRelativePath, expectedPrependedLines, expectedIncludePaths,
                    expectedSystemIncludePaths, false);
        }

        /**
         * Creates a new verifier for a file in the composition with the specified paths to the original file name and
         * the file name generated by the composer. The verifier checks that the file has the expected preprocessor
         * directives prepended.
         *
         * @param originalRelativePath       the relative path to the original file
         * @param composedRelativePath       the relative path to the file in the composition
         * @param expectedPrependedLines     the expected preprocessor directives
         * @param expectedIncludePaths       the expected include paths for this file
         * @param expectedSystemIncludePaths the expected system include paths for this file
         * @param useComposerTmpDir          whether to read the original file from the composer's temporary directory
         */
        public FileContentVerifier(Path originalRelativePath, Path composedRelativePath,
                                   List<String> expectedPrependedLines, List<Path> expectedIncludePaths,
                                   List<Path> expectedSystemIncludePaths, boolean useComposerTmpDir) {
            this.expectedIncludePaths = expectedIncludePaths;
            this.expectedSystemIncludePaths = expectedSystemIncludePaths;
            this.useComposerTmpDir = useComposerTmpDir;
            if (originalRelativePath.isAbsolute())
                throw new IllegalArgumentException("originalRelativePath must be relative");
            if (composedRelativePath.isAbsolute())
                throw new IllegalArgumentException("composedRelativePath must be relative");

            this.originalRelativePath = originalRelativePath;
            this.composedRelativePath = composedRelativePath;
            this.expectedPrependedLines = expectedPrependedLines;
        }

        /**
         * Creates a new verifier for a file in the composition with the specified inclusion information. The verifier
         * checks that the file has the expected preprocessor directives prepended. It uses the inclusion information to
         * determine the new file name generated by the composer.
         *
         * @param inclusionInformation the inclusion information
         */
        public FileContentVerifier(InclusionInformation inclusionInformation) {
            this(inclusionInformation, false);
        }

        /**
         * Creates a new verifier for a file in the composition with the specified inclusion information. The verifier
         * checks that the file has the expected preprocessor directives prepended. It uses the inclusion information to
         * determine the new file name generated by the composer.
         *
         * @param inclusionInformation the inclusion information
         * @param useComposerTmpDir    whether to read the original file from the composer's temporary directory
         */
        public FileContentVerifier(InclusionInformation inclusionInformation, boolean useComposerTmpDir) {
            this.originalRelativePath = inclusionInformation.filePath();
            this.composedRelativePath = inclusionInformation.getComposedFilePath();
            this.expectedPrependedLines = Stream.concat(
                            inclusionInformation.defines().entrySet().stream()
                                    .map(entry -> "#define %s %s".formatted(entry.getKey(), entry.getValue())),
                            inclusionInformation.includedFiles().stream()
                                    .map(includedFile -> this.originalRelativePath.getParent()
                                            .relativize(includedFile)
                                    )
                                    .map("#include \"%s\""::formatted)
                    )
                    .toList();
            this.expectedIncludePaths = inclusionInformation.includePaths();
            this.expectedSystemIncludePaths = inclusionInformation.systemIncludePaths();
            this.useComposerTmpDir = useComposerTmpDir;
        }

        @Override
        public void verify(CompositionInformation compositionInformation, KconfigTestCaseManager testCaseManager,
                           Path originalDirectory, Path compositionDirectory, Path composerTmpDirectory)
                throws IOException {
            Path compositionPath = compositionDirectory.resolve(this.composedRelativePath);
            Path originalPath = (this.useComposerTmpDir ? composerTmpDirectory.resolve("source") : originalDirectory)
                    .resolve(this.originalRelativePath);

            assertTrue(Files.exists(compositionPath),
                    "File " + this.composedRelativePath + " should exist");
            verifySourceMap(compositionInformation);

            Charset charset = Charset.forName(testCaseManager.getMetadata().encoding());
            List<String> compositionLines = Files.readAllLines(compositionPath, charset);
            List<String> originalLines = Files.readAllLines(originalPath, charset);
            assertEquals(new HashSet<>(this.expectedPrependedLines),
                    new HashSet<>(compositionLines.subList(0, this.expectedPrependedLines.size()))
            );
            assertEquals(originalLines,
                    compositionLines.subList(this.expectedPrependedLines.size(), compositionLines.size())
            );
            verifyPresenceConditions(compositionInformation, testCaseManager);
            verifyIncludePaths(compositionInformation);
        }

        protected void verifySourceMap(CompositionInformation compositionInformation) {
            assertEquals(1,
                    compositionInformation.getSourceMap()
                            .getOriginalLocation(
                                    new SourceLocation(this.composedRelativePath,
                                            this.expectedPrependedLines.size() + 1)
                            )
                            .map(SourceLocation::line)
                            .orElseThrow()
            );
        }

        private void verifyPresenceConditions(CompositionInformation compositionInformation,
                                              KconfigTestCaseManager testCaseManager) {
            Optional<List<PresenceConditionExpectation>> expectedPresenceConditionsOptional = testCaseManager.
                    getPresenceConditionExpectations(this.originalRelativePath);
            if (expectedPresenceConditionsOptional.isEmpty())
                return;
            List<PresenceConditionExpectation> expectedPresenceConditions = expectedPresenceConditionsOptional.get();

            PresenceConditionMapper presenceConditionMapper = compositionInformation.getPresenceConditionMapper();
            for (int i = 1; i <= expectedPresenceConditions.size() + this.expectedPrependedLines.size(); i++) {
                if (i <= this.expectedPrependedLines.size()) {
                    assertTrue(presenceConditionMapper.getPresenceCondition(this.composedRelativePath, i).isEmpty(),
                            "Presence condition of line %d of %s should be absent"
                                    .formatted(i, this.composedRelativePath)
                    );
                    continue;
                }
                int originalLineIndex = i - 1 - this.expectedPrependedLines.size();
                PresenceConditionExpectation presenceConditionExpectation
                        = expectedPresenceConditions.get(originalLineIndex);
                Optional<Node> determinedPresenceCondition = presenceConditionMapper
                        .getPresenceCondition(this.composedRelativePath, i);
                if (!presenceConditionExpectation.isOptional()) {
                    if (presenceConditionExpectation.getPresenceCondition().isEmpty()) {
                        assertTrue(determinedPresenceCondition.isEmpty(),
                                "Presence condition of line %d of %s should be absent"
                                        .formatted(i, this.composedRelativePath)
                        );
                    }
                    if (presenceConditionExpectation.getPresenceCondition().isPresent()) {
                        assertTrue(determinedPresenceCondition.isPresent(),
                                "Presence condition of line %d of %s should be present"
                                        .formatted(i, this.composedRelativePath)
                        );
                    }
                }
                if (presenceConditionExpectation.getPresenceCondition().isEmpty()
                        || determinedPresenceCondition.isEmpty())
                    continue;
                Node expectedPresenceCondition = presenceConditionExpectation.getPresenceCondition().get();

                assertTrue(
                        ConditionUtils.areEquivalent(expectedPresenceCondition, determinedPresenceCondition.get()),
                        "Presence condition of line %d of %s should be %s but was %s"
                                .formatted(i, this.composedRelativePath, expectedPresenceCondition,
                                        determinedPresenceCondition.get())
                );
            }
        }

        private void verifyIncludePaths(CompositionInformation compositionInformation) {
            if (this.expectedIncludePaths == null)
                return;
            List<LanguageInformation> languageInformation = compositionInformation.getLanguageInformation();
            assertTrue(languageInformation.size() == 1 && languageInformation.get(0) instanceof CCPPLanguageInformation,
                    "Composition should contain exactly one C/C++ language information");
            CCPPLanguageInformation ccppLanguageInformation = (CCPPLanguageInformation) languageInformation.get(0);
            assertEquals(
                    this.expectedIncludePaths,
                    ccppLanguageInformation.getIncludePaths().get(this.composedRelativePath)
            );
            assertEquals(
                    this.expectedSystemIncludePaths,
                    ccppLanguageInformation.getSystemIncludePaths().get(this.composedRelativePath)
            );
        }
    }

    private static class FiascoFileContentVerifier implements Verifier {
        private final InclusionInformation inclusionInformation;
        private final Path mainCCPath;
        private final Path mainHPath;
        private final Path mainIHPath;
        private final List<Verifier> fileContentVerifiers;

        /**
         * Creates a new verifier for the files in the composition created from the files the fiasco preprocessor
         * generates.
         *
         * @param inclusionInformation the inclusion information for original source file (i.e., the *.cpp file). This
         *                             is a slight abuse of {@link InclusionInformation} since the file is not directly
         *                             included in the composition but rather its preprocessed output is.
         */
        public FiascoFileContentVerifier(InclusionInformation inclusionInformation) {
            this.inclusionInformation = inclusionInformation;
            this.mainCCPath = getBuildPath(inclusionInformation.filePath(), ".cc");
            this.mainHPath = getBuildPath(inclusionInformation.filePath(), ".h");
            this.mainIHPath = getBuildPath(inclusionInformation.filePath(), "_i.h");
            this.fileContentVerifiers = List.of(
                    new FileContentVerifier(
                            new InclusionInformation(
                                    this.mainCCPath,
                                    inclusionInformation.includedFiles(),
                                    inclusionInformation.defines(),
                                    inclusionInformation.includePaths(),
                                    inclusionInformation.systemIncludePaths()
                            ),
                            true
                    ),
                    new FileContentVerifier(this.mainHPath, true),
                    new FileContentVerifier(this.mainIHPath, true)
            );
        }

        private static Path getBuildPath(Path originalPath, String ending) {
            Path originalFileName = originalPath.getFileName();
            int dotIndex = originalFileName.toString().lastIndexOf('.');
            Path buildFileName = Path.of(originalFileName.toString().substring(0, dotIndex) + ending);
            return Path.of("build/auto").resolve(buildFileName);
        }

        @Override
        public void verify(CompositionInformation compositionInformation, KconfigTestCaseManager testCaseManager,
                           Path originalDirectory, Path compositionDirectory, Path composerTmpDirectory)
                throws IOException {
            for (Verifier verifier : this.fileContentVerifiers) {
                verifier.verify(compositionInformation, testCaseManager, originalDirectory, compositionDirectory,
                        composerTmpDirectory);
            }

            this.verifySourceMaps(compositionInformation, originalDirectory, compositionDirectory);
        }

        private void verifySourceMaps(CompositionInformation compositionInformation, Path originalDirectory,
                                      Path compositionDirectory) throws IOException {
            List<String> originalLines = Files.readAllLines(
                    originalDirectory.resolve(this.inclusionInformation.filePath())
            );
            Map<String, List<Integer>> originalLineNumbers = new HashMap<>();
            for (int i = 0; i < originalLines.size(); i++) {
                String line = originalLines.get(i);
                originalLineNumbers.computeIfAbsent(line, k -> new ArrayList<>()).add(i + 1);
            }
            this.verifySourceMapOfFile(compositionInformation, new InclusionInformation(
                            this.mainCCPath,
                            this.inclusionInformation.includedFiles(),
                            this.inclusionInformation.defines(),
                            this.inclusionInformation.includePaths(),
                            this.inclusionInformation.systemIncludePaths()
                    ).getComposedFilePath(), compositionDirectory,
                    originalLineNumbers, false);
            this.verifySourceMapOfFile(compositionInformation, this.mainHPath, compositionDirectory,
                    originalLineNumbers, true);
            this.verifySourceMapOfFile(compositionInformation, this.mainIHPath, compositionDirectory,
                    originalLineNumbers, true);
        }

        private void verifySourceMapOfFile(CompositionInformation compositionInformation, Path path,
                                           Path compositionDirectory, Map<String, List<Integer>> originalLineNumbers,
                                           boolean isHeader) throws IOException {
            // Number of lines prepended by the composer. These lines don't have source map information, so we'll ignore
            // them.
            int addedLines = isHeader ? this.inclusionInformation.defines().size()
                    + this.inclusionInformation.includedFiles().size()
                    : 0;

            List<String> compositionLines = Files.readAllLines(compositionDirectory.resolve(path));
            SourceMap sourceMap = compositionInformation.getSourceMap();
            for (int i = 0; i < compositionLines.size(); i++) {
                String line = compositionLines.get(i);
                if (i < addedLines) {
                    continue;
                }

                if (line.isBlank()) {
                    // Blank lines are generated by fiasco's preprocessor. We don't know if this line is generated or
                    // not, but in any case it is very unlikely that a blank line is mapped incorrectly while other
                    // lines are mapped correctly.
                    continue;
                }

                if (!originalLineNumbers.containsKey(line)) {
                    // This line was (hopefully) generated by fiasco's preprocessor. We don't have source map
                    // information for it, so we'll ignore it.
                    continue;
                }

                SourceLocation reportedOriginalLocation = sourceMap.getOriginalLocation(new SourceLocation(path, i + 1))
                        .orElseThrow();
                assertEquals(this.inclusionInformation.filePath(), reportedOriginalLocation.file(),
                        "Original path of line %d of %s should be %s but was %s"
                                .formatted(i + 1, this.inclusionInformation.filePath(), path,
                                        reportedOriginalLocation.file())
                );
                List<Integer> originalLineNumbersList = originalLineNumbers.get(line);
                assertTrue(originalLineNumbersList.contains(reportedOriginalLocation.line()),
                        "Original line number %d not found in potential locations for line %d of %s"
                                .formatted(reportedOriginalLocation.line(), i + 1, path)
                );
            }
        }
    }
}