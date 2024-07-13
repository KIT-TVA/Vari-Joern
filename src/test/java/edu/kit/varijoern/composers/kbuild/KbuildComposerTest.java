package edu.kit.varijoern.composers.kbuild;

import edu.kit.varijoern.ConditionUtils;
import edu.kit.varijoern.KconfigTestCaseManager;
import edu.kit.varijoern.KconfigTestCasePreparer;
import edu.kit.varijoern.PresenceConditionExpectation;
import edu.kit.varijoern.composers.*;
import edu.kit.varijoern.composers.sourcemap.SourceLocation;
import edu.kit.varijoern.samplers.FixedSampler;
import edu.kit.varijoern.samplers.SamplerException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.prop4j.Node;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class KbuildComposerTest {
    private static final List<KconfigTestCasePreparer> STANDARD_PREPARERS = List.of(
            (path) -> {
            },
            KbuildComposerTest::buildAllYesConfig
    );

    private static Stream<Arguments> testCases() {
        return Stream.of(busyboxTestCases(), linuxTestCases(), fiascoTestCases())
                .flatMap(s -> s);
    }

    private static Stream<Arguments> busyboxTestCases() {
        Set<Path> standardIncludedFiles = Set.of(Path.of("include/autoconf.h"),
                Path.of("include/cmdline-included-header.h"));
        InclusionInformation mainC = new InclusionInformation(
                Path.of("src/main.c"),
                standardIncludedFiles,
                standardBusyboxDefinesForFile("main"),
                List.of(Path.of("include"))
        );
        InclusionInformation includedCByMain = new InclusionInformation(
                Path.of("src/included.c"),
                standardIncludedFiles,
                standardBusyboxDefinesForFile("main"),
                List.of(Path.of("include"))
        );
        InclusionInformation includedCByIO = new InclusionInformation(
                Path.of("src/included.c"),
                standardIncludedFiles,
                standardBusyboxDefinesForFile("io_file"),
                List.of(Path.of("include"))
        );
        Stream<TestCase> testCases = Stream.of(
                new TestCase(
                        "busybox-sample",
                        "busybox",
                        List.of(),
                        List.of(
                                new FileAbsentVerifier(".*\\.src"),
                                new FileAbsentVerifier(".*\\.in"),
                                new FileAbsentVerifier(".*\\.o"),
                                FileAbsentVerifier.originalSourceAndHeader("src/hello-cpp"),
                                FileAbsentVerifier.originalSourceAndHeader("src/io-file"),
                                new FileContentVerifier(mainC),
                                new FileContentVerifier(includedCByMain),
                                new FileContentVerifier(Path.of("src/main.h")),
                                new FileContentVerifier(Path.of("include/cmdline-included-header.h"))
                        )
                ),
                new TestCase(
                        "busybox-sample",
                        "busybox",
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
                                        List.of(Path.of("include"))
                                )),
                                new FileContentVerifier(Path.of("src/hello-cpp.h")),
                                new FileContentVerifier(new InclusionInformation(
                                        Path.of("src/io-file.c"),
                                        standardIncludedFiles,
                                        standardBusyboxDefinesForFile("io_file"),
                                        List.of(Path.of("include"))
                                )),
                                new FileContentVerifier(includedCByIO),
                                new FileContentVerifier(Path.of("src/io-file.h")),
                                new FileContentVerifier(mainC),
                                new FileContentVerifier(includedCByMain),
                                new FileContentVerifier(Path.of("src/main.h")),
                                new FileContentVerifier(Path.of("include/cmdline-included-header.h"))
                        )
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
        InclusionInformation mainC = new InclusionInformation(
                Path.of("src/main.c"),
                standardIncludedFiles,
                standardLinuxDefinesForFile("main"),
                standardIncludePaths
        );
        Stream<TestCase> testCases = Stream.of(
                new TestCase(
                        "linux-sample",
                        "linux",
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
                                        standardIncludePaths
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
        Path gccIncludePath = getGCCIncludePath();
        List<Path> standardIncludePaths = List.of(Path.of("build"), Path.of("build/auto"));
        FileContentVerifier mainVerifier = new FileContentVerifier(new InclusionInformation(
                Path.of("build/auto/main.cc"),
                Set.of(),
                Map.of(),
                standardIncludePaths
        ), true); // The build/* files aren't in the original source, so they must be read from the
        // composer's tmp dir, which will run fiasco's preprocessor to generate them
        FileContentVerifier mainHVerifier = new FileContentVerifier(Path.of("build/auto/main.h"), true);
        FileContentVerifier mainIHVerifier = new FileContentVerifier(Path.of("build/auto/main_i.h"), true);
        Stream<TestCase> testCases = Stream.of(
                new TestCase(
                        "fiasco-sample",
                        "fiasco",
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
                                mainVerifier,
                                mainHVerifier,
                                mainIHVerifier
                        )
                ),
                new TestCase(
                        "fiasco-sample",
                        "fiasco",
                        List.of(
                                "INCLUDE_IO_FILE",
                                "PERFORM_RENAME",
                                "PERFORM_CHMOD",
                                "USE_GETS",
                                "AMD64",
                                "__VISIBILITY__CONFIG_CC",
                                "__VISIBILITY__CONFIG_CXX",
                                "__VISIBILITY__CONFIG_LD",
                                "__VISIBILITY__CONFIG_HOST_CC",
                                "__VISIBILITY__CONFIG_HOST_CXX"
                        ),
                        List.of(
                                new FileAbsentVerifier(".*\\.o"),
                                new FileContentVerifier(new InclusionInformation(
                                        Path.of("build/auto/io_file.cc"),
                                        Set.of(),
                                        Map.of(),
                                        standardIncludePaths
                                ), true),
                                new FileContentVerifier(Path.of("build/auto/io_file.h"), true),
                                new FileContentVerifier(Path.of("build/auto/io_file_i.h"), true),
                                mainVerifier,
                                mainHVerifier,
                                mainIHVerifier
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
            ));
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
    void runTestCase(TestCase testCase, KconfigTestCasePreparer preparer)
            throws IOException, GitAPIException, InterruptedException {
        KconfigTestCaseManager testCaseManager = new KconfigTestCaseManager(testCase.name, preparer);
        Map<String, Boolean> featureMap;
        try {
            featureMap = new FixedSampler(List.of(testCase.enabledFeatures), testCaseManager.getCorrectFeatureModel())
                    .sample(List.of())
                    .get(0);
        } catch (SamplerException e) {
            throw new RuntimeException(e);
        }

        Path originalDirectory = testCaseManager.getPath();
        Path destinationBaseDirectory = Files.createTempDirectory("vari-joern-test-kbuild-composer");
        Path destinationDirectory = Files.createDirectory(destinationBaseDirectory.resolve("composition"));
        Path composerTmpDirectory = Files.createDirectory(destinationBaseDirectory.resolve("tmp"));

        System.err.println("Created temporary directory " + destinationBaseDirectory);

        CompositionInformation compositionInformation;
        try {
            Composer composer = new KbuildComposer(testCaseManager.getPath(), testCase.system, composerTmpDirectory);
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
     * A test case for the {@link KbuildComposer}.
     *
     * @param name            the name of the test case
     * @param system          the Kconfig/Kbuild implementation of the test case
     * @param enabledFeatures the features to enable for the test case
     * @param verifiers       the verifiers to run on the composition
     */
    private record TestCase(String name, String system, List<String> enabledFeatures,
                            List<Verifier> verifiers) {
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
         * generated by the {@link KbuildComposer}, the applied regular expression is {@code name[-0-9]*\.(cc|c|h)}.
         *
         * @param name the file to check for, without the extension
         * @return the verifier
         */
        public static Verifier originalSourceAndHeader(String name) {
            return new FileAbsentVerifier(name + "[-0-9]*\\.(cc|c|h)");
        }

        /**
         * Returns a verifier that checks that there are no files generated by Fiasco's C++ preprocessor.
         * Forbidden files are:
         * <ul>
         *     <li>{@code build/auto/name.cc}</li>
         *     <li>{@code build/auto/name.h}</li>
         *     <li>{@code build/auto/name_i.h}</li>
         * </ul>
         * where {@code name} is the specified name. To make sure that the verifier also matches the file names
         * generated by the {@link KbuildComposer}, the applied regular expression is
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
        private final boolean useComposerTmpDir;

        /**
         * Creates a new verifier for a file in the composition. This constructor assumes that the file is not renamed
         * by the composer and that no prepended preprocessor directives are expected.
         *
         * @param relativePath the relative path to the file
         */
        public FileContentVerifier(Path relativePath) {
            this(relativePath, relativePath, List.of(), null);
        }

        /**
         * Creates a new verifier for a file in the composition. This constructor assumes that the file is not renamed
         * by the composer and that no prepended preprocessor directives are expected.
         *
         * @param relativePath      the relative path to the file
         * @param useComposerTmpDir whether to read the original file from the composer's temporary directory
         */
        public FileContentVerifier(Path relativePath, boolean useComposerTmpDir) {
            this(relativePath, relativePath, List.of(), null, useComposerTmpDir);
        }

        /**
         * Creates a new verifier for a file in the composition with the specified paths to the original file name and
         * the file name generated by the composer. The verifier checks that the file has the expected preprocessor
         * directives prepended.
         *
         * @param originalRelativePath   the relative path to the original file
         * @param composedRelativePath   the relative path to the file in the composition
         * @param expectedPrependedLines the expected preprocessor directives
         * @param expectedIncludePaths   the expected include paths for this file
         */
        public FileContentVerifier(Path originalRelativePath, Path composedRelativePath,
                                   List<String> expectedPrependedLines, List<Path> expectedIncludePaths) {
            this(originalRelativePath, composedRelativePath, expectedPrependedLines, expectedIncludePaths, false);
        }

        /**
         * Creates a new verifier for a file in the composition with the specified paths to the original file name and
         * the file name generated by the composer. The verifier checks that the file has the expected preprocessor
         * directives prepended.
         *
         * @param originalRelativePath   the relative path to the original file
         * @param composedRelativePath   the relative path to the file in the composition
         * @param expectedPrependedLines the expected preprocessor directives
         * @param expectedIncludePaths   the expected include paths for this file
         * @param useComposerTmpDir      whether to read the original file from the composer's temporary directory
         */
        public FileContentVerifier(Path originalRelativePath, Path composedRelativePath,
                                   List<String> expectedPrependedLines, List<Path> expectedIncludePaths,
                                   boolean useComposerTmpDir) {
            this.expectedIncludePaths = expectedIncludePaths;
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

            List<String> compositionLines = Files.readAllLines(compositionPath);
            List<String> originalLines = Files.readAllLines(originalPath);
            assertEquals(new HashSet<>(this.expectedPrependedLines),
                    new HashSet<>(compositionLines.subList(0, this.expectedPrependedLines.size()))
            );
            assertEquals(originalLines,
                    compositionLines.subList(this.expectedPrependedLines.size(), compositionLines.size())
            );
            verifyPresenceConditions(compositionInformation, testCaseManager);
            verifyIncludePaths(compositionInformation);
        }

        private void verifySourceMap(CompositionInformation compositionInformation) {
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
            assertEquals(this.expectedIncludePaths,
                    ccppLanguageInformation.getIncludePaths().get(this.composedRelativePath)
            );
        }
    }
}