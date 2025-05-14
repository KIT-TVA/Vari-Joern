package edu.kit.varijoern.analyzers.joern;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.ConditionUtils;
import edu.kit.varijoern.KconfigTestCaseManager;
import edu.kit.varijoern.analyzers.AnalyzerFailureException;
import edu.kit.varijoern.analyzers.FindingAggregation;
import edu.kit.varijoern.analyzers.ResultAggregator;
import edu.kit.varijoern.composers.Composer;
import edu.kit.varijoern.composers.ComposerException;
import edu.kit.varijoern.composers.CompositionInformation;
import edu.kit.varijoern.composers.kbuild.KbuildComposer;
import edu.kit.varijoern.composers.kbuild.subjects.*;
import edu.kit.varijoern.composers.sourcemap.SourceLocation;
import edu.kit.varijoern.featuremodel.FeatureModelReaderException;
import edu.kit.varijoern.samplers.FixedSampler;
import edu.kit.varijoern.samplers.Sampler;
import edu.kit.varijoern.samplers.SamplerException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.prop4j.And;
import org.prop4j.Literal;
import org.prop4j.Node;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JoernAnalyzerTest {
    /**
     * Tests the analysis of the BusyBox sample with two different configurations.
     */
    @Test
    void analyzeBusybox(@TempDir Path tempDirectory)
            throws IOException, GitAPIException, ComposerException, AnalyzerFailureException, InterruptedException,
            FeatureModelReaderException {
        KconfigTestCaseManager testCaseManager = new KconfigTestCaseManager("busybox-sample");

        List<Map<String, Boolean>> configurations = Stream.of(
                        List.of("USE_GETS", "INCLUDE_IO_FILE", "PERFORM_CHMOD", "USE_CPP_FILE"),
                        List.of("USE_GETS", "INCLUDE_IO_FILE", "PERFORM_CHMOD", "PERFORM_RENAME")
                )
                .map(configuration -> {
                    Sampler sampler = new FixedSampler(List.of(configuration),
                            testCaseManager.getCorrectFeatureModel());
                    try {
                        return sampler.sample(null, tempDirectory.resolve("sampler")).get(0);
                    } catch (SamplerException | InterruptedException | IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();

        List<ExpectedFinding> expectedFindings = List.of(
                new ExpectedFinding("call-to-gets",
                        Set.of(new SourceLocation(Path.of("src/main.c"), 17)),
                        new Literal("USE_GETS"),
                        configurations
                ),
                new ExpectedFinding("file-operation-race",
                        Set.of(new SourceLocation(Path.of("src/io-file.c"), 22)),
                        new And(new Literal("INCLUDE_IO_FILE"), new Literal("PERFORM_CHMOD")),
                        List.of(configurations.get(1))
                ),
                new ExpectedFinding("file-operation-race",
                        Set.of(new SourceLocation(Path.of("src/io-file.c"), 25)),
                        new And(new Literal("INCLUDE_IO_FILE"), new Literal("PERFORM_RENAME")),
                        List.of(configurations.get(1))
                ),
                new ExpectedFinding("malloc-memcpy-int-overflow",
                        Set.of(new SourceLocation(Path.of("src/hello-cpp.cc"), 11)),
                        new Literal("USE_CPP_FILE"),
                        List.of(configurations.get(0))
                )
        );

        analyze(tempDirectory, testCaseManager, new BusyboxStrategyFactory(), expectedFindings,
                configurations);
    }

    /**
     * Tests the analysis of the fiasco sample with two different configurations.
     */
    @Test
    void analyzeFiasco(@TempDir Path tempDirectory)
            throws IOException, GitAPIException, ComposerException, AnalyzerFailureException, InterruptedException,
            FeatureModelReaderException {
        KconfigTestCaseManager testCaseManager = new KconfigTestCaseManager("fiasco-sample");

        List<Map<String, Boolean>> configurations = Stream.of(
                        List.of("USE_GETS",
                                "INCLUDE_IO_FILE",
                                "PERFORM_CHMOD",
                                "DEFINE_USELESS_FUNCTION",
                                "AMD64",
                                "__VISIBILITY__CONFIG_CC",
                                "__VISIBILITY__CONFIG_CXX",
                                "__VISIBILITY__CONFIG_LD",
                                "__VISIBILITY__CONFIG_HOST_CC",
                                "__VISIBILITY__CONFIG_HOST_CXX"),
                        List.of("USE_GETS",
                                "INCLUDE_IO_FILE",
                                "PERFORM_CHMOD",
                                "PERFORM_RENAME",
                                "AMD64",
                                "__VISIBILITY__CONFIG_CC",
                                "__VISIBILITY__CONFIG_CXX",
                                "__VISIBILITY__CONFIG_LD",
                                "__VISIBILITY__CONFIG_HOST_CC",
                                "__VISIBILITY__CONFIG_HOST_CXX")
                )
                .map(configuration -> {
                    Sampler sampler = new FixedSampler(List.of(configuration),
                            testCaseManager.getCorrectFeatureModel());
                    try {
                        return sampler.sample(null, tempDirectory.resolve("sampler")).get(0);
                    } catch (SamplerException | InterruptedException | IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();

        List<ExpectedFinding> expectedFindings = List.of(
                new ExpectedFinding("call-to-gets",
                        Set.of(new SourceLocation(Path.of("src/sample/main.cpp"), 23)),
                        null,
                        configurations
                ),
                new ExpectedFinding("file-operation-race",
                        Set.of(new SourceLocation(Path.of("src/sample/io_file.cpp"), 11)),
                        null,
                        List.of(configurations.get(1))
                ),
                new ExpectedFinding("file-operation-race",
                        Set.of(new SourceLocation(Path.of("src/sample/io_file.cpp"), 14)),
                        null,
                        List.of(configurations.get(1))
                )
        );

        analyze(tempDirectory, testCaseManager, new FiascoStrategyFactory(), expectedFindings,
                configurations);
    }

    private void analyze(Path tempDirectory, KconfigTestCaseManager testCaseManager,
                         @NotNull ComposerStrategyFactory composerStrategyFactory,
                         List<ExpectedFinding> expectedFindings, List<Map<String, Boolean>> configurations)
            throws IOException, ComposerException, InterruptedException, AnalyzerFailureException {
        Path workspaceDirectory = tempDirectory.resolve("workspace");
        Files.createDirectory(workspaceDirectory);
        ResultAggregator<JoernAnalysisResult, JoernFinding> resultAggregator = new JoernResultAggregator();
        JoernAnalyzer analyzer = new JoernAnalyzer(null, workspaceDirectory, resultAggregator);

        Path composerTempDirectory = tempDirectory.resolve("composer");
        Composer composer = new KbuildComposer(testCaseManager.getPath(), composerStrategyFactory,
                Charset.forName(testCaseManager.getMetadata().encoding()), composerTempDirectory, Set.of(),
                false);

        for (int i = 0; i < configurations.size(); i++) {
            Map<String, Boolean> configuration = configurations.get(i);
            JoernAnalysisResult result = this.analyzeVariant(configuration, testCaseManager.getCorrectFeatureModel(),
                    composer, analyzer, tempDirectory.resolve(String.valueOf(i)));
            this.verifyFindings(result.getFindings(), expectedFindings, configuration);
        }

        this.verifyAggregatedFindings(resultAggregator.aggregateResults().findingAggregations(), expectedFindings);
    }

    private JoernAnalysisResult analyzeVariant(Map<String, Boolean> configuration, IFeatureModel featureModel,
                                               Composer composer, JoernAnalyzer analyzer, Path destinationDirectory)
            throws ComposerException, IOException, AnalyzerFailureException, InterruptedException {
        CompositionInformation compositionInformation = composer.compose(configuration, destinationDirectory,
                featureModel);
        return analyzer.analyze(compositionInformation);
    }

    /**
     * Verifies that all expected findings are found and that no unexpected findings are reported. Findings for code
     * that is not in the src directory are ignored.
     *
     * @param findings         the reported findings
     * @param expectedFindings the expected findings
     * @param configuration    the configuration that was used to generate the findings
     */
    private void verifyFindings(List<JoernFinding> findings, List<ExpectedFinding> expectedFindings,
                                Map<String, Boolean> configuration) {
        BiPredicate<ExpectedFinding, JoernFinding> findingsEqual = (expectedFinding, finding) -> {
            if (!finding.getName().equals(expectedFinding.name)) {
                return false;
            }
            if (!finding.getEvidence().equals(expectedFinding.evidence)) {
                return false;
            }
            return finding.getCondition() == null && expectedFinding.presenceCondition == null
                    || ConditionUtils.areEquivalent(finding.getCondition(), expectedFinding.presenceCondition);
        };
        this.verifyFindingCollection(
                findings.stream()
                        .filter(finding -> finding.getEvidence().stream().findAny()
                                .map(location -> location.file().startsWith("src"))
                                .orElse(false)
                        )
                        .toList(),
                expectedFindings, configuration, findingsEqual
        );
    }

    /**
     * Verifies that all expected aggregated findings are found and that no unexpected findings are reported. Findings
     * for code that is not in the src directory are ignored.
     *
     * @param findingAggregations the reported aggregated findings
     * @param expectedFindings    the expected findings
     */
    private void verifyAggregatedFindings(Set<FindingAggregation> findingAggregations,
                                          List<ExpectedFinding> expectedFindings) {
        this.verifyFindingCollection(
                findingAggregations.stream()
                        .filter(finding -> finding.getOriginalEvidenceLocations().stream().findAny()
                                .map(location -> location.file().startsWith("src"))
                                .orElse(false)
                        )
                        .toList(),
                expectedFindings, null,
                (expectedFinding, finding) -> {
                    if (!finding.getFinding().getName().equals(expectedFinding.name))
                        return false;

                    if (!finding.getOriginalEvidenceLocations().equals(expectedFinding.evidence))
                        return false;

                    // In our test cases, we know that there is at most one possible condition
                    if (finding.getPossibleConditions().size() > 1)
                        return false;

                    return finding.getPossibleConditions().isEmpty() && expectedFinding.presenceCondition == null
                            || ConditionUtils.areEquivalent(
                            finding.getPossibleConditions().stream().iterator().next(),
                            expectedFinding.presenceCondition
                    );
                }
        );
    }

    private <T> void verifyFindingCollection(List<T> findings, List<ExpectedFinding> expectedFindings,
                                             Map<String, Boolean> configuration,
                                             BiPredicate<ExpectedFinding, T> findingsEqual) {
        int foundExpectedFindings = 0;
        for (ExpectedFinding expectedFinding : expectedFindings) {
            if (configuration != null && !expectedFinding.affectedVariants.contains(configuration)) {
                continue;
            }
            assertEquals(1,
                    findings.stream()
                            .filter(finding -> findingsEqual.test(expectedFinding, finding))
                            .count(),
                    "Finding not found or reported more than once: %s in %s"
                            .formatted(expectedFinding.name, findings)
            );
            foundExpectedFindings++;
        }

        assertEquals(findings.size(), foundExpectedFindings,
                "Unexpected findings were reported: %s".formatted(findings));
    }

    private record ExpectedFinding(String name, Set<SourceLocation> evidence, @Nullable Node presenceCondition,
                                   List<Map<String, Boolean>> affectedVariants) {
    }
}
