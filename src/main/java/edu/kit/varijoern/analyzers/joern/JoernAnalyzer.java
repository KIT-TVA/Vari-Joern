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
    private static final Logger LOGGER = LogManager.getLogger();
    private static final OutputStream STREAM_LOGGER = IoBuilder.forLogger().setLevel(Level.DEBUG).buildOutputStream();

    private final @Nullable Path joernPath;
    private final @NotNull Path workspacePath;
    private final @NotNull Path scanScriptPath;
    private final @NotNull List<JoernAnalysisResult> allAnalysisResults = new ArrayList<>();

    /**
     * Creates a new {@link JoernAnalyzer} instance which uses the specified command name to run joern-scan.
     *
     * @param joernPath     the path to the directory containing the joern executables. May be null to use the system
     *                      PATH.
     * @param workspacePath the directory to use for Joern's workspace folder. Must be an absolute path.
     */
    public JoernAnalyzer(@Nullable Path joernPath, @NotNull Path workspacePath) throws IOException {
        this.joernPath = joernPath;
        this.workspacePath = workspacePath;

        String scannerScript = ResourcesUtil.getResourceAsString("joern/scan.sc");
        this.scanScriptPath = workspacePath.resolve("scan.sc");
        Files.writeString(this.scanScriptPath, scannerScript, StandardCharsets.UTF_8);
    }

    @Override
    public @NotNull JoernAnalysisResult analyze(@NotNull CompositionInformation compositionInformation)
            throws IOException, AnalyzerFailureException {
        LOGGER.info("Running analysis");
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
                compositionInformation.getPresenceConditionMapper(),
                compositionInformation.getSourceMap());
        this.allAnalysisResults.add(result);
        LOGGER.info("Analysis finished");
        return result;
    }

    @Override
    public @NotNull AggregatedAnalysisResult aggregateResults() {
        // Group findings by their evidence and query name, store the analysis result retrieve the enabled features
        // and the presence condition mappers later
        Map<?, List<Pair<AnnotatedFinding, JoernAnalysisResult>>> groupedFindings
                = this.allAnalysisResults.stream()
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

    private void analyzeWithLanguageInformation(@NotNull LanguageInformation languageInformation,
                                                @NotNull Path sourceLocation, @NotNull Path outFile)
            throws AnalyzerFailureException, IOException {
        LOGGER.info("Running analysis for language {}", languageInformation.getName());
        Path cpgLocation = this.workspacePath.resolve("cpg.bin");
        LOGGER.info("Generating CPG");
        languageInformation.accept(
                new CPGGenerationVisitor(sourceLocation, cpgLocation, this.joernPath)
        );
        LOGGER.info("Running analysis on CPG");
        runJoern(cpgLocation, outFile);
    }

    /**
     * Runs Joern on the specified source code. The results are saved in the JSON format in the specified file.
     *
     * @param cpgLocation the location of the code property graph. Must be an absolute path.
     * @param outFile     the path of the file to save the findings in. Must be an absolute path.
     * @throws IOException              if an I/O error occurred
     * @throws AnalyzerFailureException if Joern exited with a non-zero exit code
     */
    private void runJoern(@NotNull Path cpgLocation, @NotNull Path outFile)
            throws IOException, AnalyzerFailureException {
        Process joernProcess = new ProcessBuilder(
                this.joernPath == null ? JOERN_COMMAND : this.joernPath.resolve(JOERN_COMMAND).toString(),
                "--script", this.scanScriptPath.toString(),
                "--param", String.format("cpgPath=%s", cpgLocation),
                "--param", String.format("outFile=%s", outFile)
        )
                .directory(this.workspacePath.toFile())
                .start();
        StreamGobbler stdoutGobbler = new StreamGobbler(joernProcess.getInputStream(), STREAM_LOGGER);
        StreamGobbler stderrGobbler = new StreamGobbler(joernProcess.getErrorStream(), STREAM_LOGGER);
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

    private @NotNull List<JoernFinding> parseFindings(@NotNull Path findingsFile)
            throws IOException, AnalyzerFailureException {
        ObjectMapper objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        List<ParsedFinding> finding = objectMapper.readValue(findingsFile.toFile(), new TypeReference<>() {
        });
        List<JoernFinding> list = new ArrayList<>();
        for (ParsedFinding parsedFinding : finding) {
            JoernFinding joernFinding = parsedFinding.toJoernFinding();
            list.add(joernFinding);
        }
        return list;
    }

    protected record ParsedFinding(@Nullable String name, @Nullable String title, @Nullable String description,
                                   double score, @Nullable List<ParsedEvidence> evidence) {
        public @NotNull JoernFinding toJoernFinding() throws AnalyzerFailureException {
            if (this.name == null || this.title == null || this.description == null || this.evidence == null)
                throw new AnalyzerFailureException("Joern output is missing required fields");
            Set<Evidence> set = new HashSet<>();
            for (ParsedEvidence parsedEvidence : this.evidence) {
                Evidence parsedEvidenceEvidence = parsedEvidence.toEvidence();
                set.add(parsedEvidenceEvidence);
            }
            return new JoernFinding(this.name, this.title, this.description, this.score,
                    set);
        }
    }

    protected record ParsedEvidence(@Nullable String filename, int lineNumber) {
        public @NotNull Evidence toEvidence() throws AnalyzerFailureException {
            if (this.filename == null)
                throw new AnalyzerFailureException("File name missing in evidence");
            return new Evidence(this.filename, this.lineNumber);
        }
    }
}
