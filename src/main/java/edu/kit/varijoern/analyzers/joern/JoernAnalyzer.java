package edu.kit.varijoern.analyzers.joern;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.kit.varijoern.analyzers.*;
import edu.kit.varijoern.composers.CompositionInformation;
import edu.kit.varijoern.composers.LanguageInformation;
import jodd.io.StreamGobbler;
import jodd.util.ResourcesUtil;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.io.IoBuilder;
import org.javatuples.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * An analyzer that uses Joern.
 */
public class JoernAnalyzer implements Analyzer {
    public static final String NAME = "joern";
    private static final String JOERN_COMMAND = "joern";
    private static final Logger logger = LogManager.getLogger();
    private static final OutputStream streamLogger = IoBuilder.forLogger().setLevel(Level.DEBUG).buildOutputStream();

    @Nullable
    private final Path joernPath;
    private final Path workspacePath;
    private final Path scanScriptPath;
    private final List<JoernAnalysisResult> allAnalysisResults = new ArrayList<>();

    /**
     * Creates a new {@link JoernAnalyzer} instance which uses the specified command name to run joern-scan.
     *
     * @param joernPath     the path to the directory containing the joern executables. May be null to use the system
     *                      PATH.
     * @param workspacePath the directory to use for Joern's workspace folder
     */
    public JoernAnalyzer(@Nullable Path joernPath, @NotNull Path workspacePath) throws IOException {
        this.joernPath = joernPath;
        this.workspacePath = workspacePath;

        String scannerScript = ResourcesUtil.getResourceAsString("joern/scan.sc");
        this.scanScriptPath = workspacePath.resolve("scan.sc");
        Files.writeString(this.scanScriptPath, scannerScript, StandardCharsets.UTF_8);
    }

    @Override
    public JoernAnalysisResult analyze(CompositionInformation compositionInformation)
            throws IOException, AnalyzerFailureException {
        logger.info("Running analysis");
        Path sourceLocation = compositionInformation.getLocation();
        Path outFile = this.workspacePath.resolve(
                String.format("%s-%s.json",
                        sourceLocation.subpath(sourceLocation.getNameCount() - 1, sourceLocation.getNameCount()),
                        UUID.randomUUID()
                )
        );
        List<JoernFinding> findings = new ArrayList<>();
        for (LanguageInformation languageInformation : compositionInformation.getLanguageInformation()) {
            this.analyzeWithLanguageInformation(languageInformation, sourceLocation, outFile);
            findings.addAll(this.parseFindings(outFile));
        }
        JoernAnalysisResult result = new JoernAnalysisResult(findings,
                compositionInformation.getEnabledFeatures(),
                compositionInformation.getFeatureMapper(),
                compositionInformation.getSourceMap());
        this.allAnalysisResults.add(result);
        logger.info("Analysis finished");
        return result;
    }

    @Override
    public AggregatedAnalysisResult aggregateResults() {
        // Group findings by their evidence and query name, store the analysis result retrieve the enabled features
        // and the feature mappers later
        Map<?, List<Pair<AnnotatedFinding, JoernAnalysisResult>>> groupedFindings =
                this.allAnalysisResults.stream()
                        .flatMap(result -> result.getFindings().stream()
                                .map(finding -> Pair.with(finding, result)))
                        .collect(Collectors.groupingBy(
                                findingPair -> Pair.with(
                                        findingPair.getValue0().originalEvidenceLocations(),
                                        ((JoernFinding) findingPair.getValue0().finding()).getName()
                                )
                        ));
        return new AggregatedAnalysisResult(groupedFindings
                .values().stream()
                .map(findingPairs -> {
                    // Use the first finding as all findings in this group are (likely) equal.
                    AnnotatedFinding firstAnnotatedFinding = findingPairs.get(0).getValue0();
                    JoernFinding firstFinding = (JoernFinding) firstAnnotatedFinding.finding();
                    return new FindingAggregation(firstFinding,
                            findingPairs.stream()
                                    .map(findingPair -> findingPair.getValue1().getEnabledFeatures())
                                    .collect(Collectors.toSet()),
                            findingPairs.stream()
                                    .map(findingPair -> findingPair.getValue0().condition())
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toSet()),
                            firstAnnotatedFinding.originalEvidenceLocations()
                    );
                })
                .collect(Collectors.toSet()));
    }

    private void analyzeWithLanguageInformation(LanguageInformation languageInformation, Path sourceLocation,
                                                Path outFile) throws AnalyzerFailureException, IOException {
        logger.info("Running analysis for language {}", languageInformation.getName());
        Path cpgLocation = this.workspacePath.resolve("cpg.bin");
        logger.info("Generating CPG");
        languageInformation.accept(
                new CPGGenerationVisitor(sourceLocation, cpgLocation, this.joernPath)
        );
        logger.info("Running analysis on CPG");
        runJoern(cpgLocation, outFile);
    }

    /**
     * Runs Joern on the specified source code. The results are saved in the JSON format in the specified file.
     *
     * @param cpgLocation the location of the code property graph
     * @param outFile     the path of the file to save the findings in
     * @throws IOException              if an I/O error occurred
     * @throws AnalyzerFailureException if Joern exited with a non-zero exit code
     */
    private void runJoern(Path cpgLocation, Path outFile) throws IOException, AnalyzerFailureException {
        Process joernProcess = new ProcessBuilder(
                this.joernPath == null ? JOERN_COMMAND : this.joernPath.resolve(JOERN_COMMAND).toString(),
                "--script", this.scanScriptPath.toString(),
                "--param", String.format("cpgPath=%s", cpgLocation),
                "--param", String.format("outFile=%s", outFile)
        )
                .directory(this.workspacePath.toFile())
                .start();
        StreamGobbler stdoutGobbler = new StreamGobbler(joernProcess.getInputStream(), streamLogger);
        StreamGobbler stderrGobbler = new StreamGobbler(joernProcess.getErrorStream(), streamLogger);
        stdoutGobbler.start();
        stderrGobbler.start();
        int joernExitCode;
        try {
            joernExitCode = joernProcess.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException("Unexpected interruption", e);
        }
        stdoutGobbler.waitFor();
        stderrGobbler.waitFor();
        if (joernExitCode != 0)
            throw new AnalyzerFailureException(String.format("joern exited with %d", joernExitCode));
    }

    private List<JoernFinding> parseFindings(Path findingsFile) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper.readValue(findingsFile.toFile(), new TypeReference<>() {
        });
    }
}
