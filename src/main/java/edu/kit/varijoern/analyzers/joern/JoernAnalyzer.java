package edu.kit.varijoern.analyzers.joern;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.kit.varijoern.analyzers.Analyzer;
import edu.kit.varijoern.analyzers.AnalyzerFailureException;
import edu.kit.varijoern.analyzers.ResultAggregator;
import edu.kit.varijoern.composers.CompositionInformation;
import edu.kit.varijoern.composers.LanguageInformation;
import edu.kit.varijoern.composers.conditionmapping.PresenceConditionMapper;
import edu.kit.varijoern.composers.sourcemap.SourceLocation;
import edu.kit.varijoern.composers.sourcemap.SourceMap;
import jodd.io.StreamGobbler;
import jodd.util.ResourcesUtil;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.io.IoBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.prop4j.Node;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
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
    private final @Nullable ResultAggregator<JoernAnalysisResult, JoernFinding> resultAggregator;

    /**
     * Creates a new {@link JoernAnalyzer} instance which uses the specified command name to run joern-scan.
     *
     * @param joernPath        the path to the directory containing the joern executables. May be null to use the system
     *                         PATH.
     * @param workspacePath    the directory to use for Joern's workspace folder. Must be an absolute path.
     * @param resultAggregator the result aggregator to use. May be null if no result aggregation is desired.
     */
    public JoernAnalyzer(@Nullable Path joernPath, @NotNull Path workspacePath,
                         @Nullable ResultAggregator<JoernAnalysisResult, JoernFinding> resultAggregator)
            throws IOException {
        this.joernPath = joernPath;
        this.workspacePath = workspacePath;
        this.resultAggregator = resultAggregator;

        String scannerScript = ResourcesUtil.getResourceAsString("joern/scan.sc");
        this.scanScriptPath = workspacePath.resolve("scan.sc");
        Files.writeString(this.scanScriptPath, scannerScript, StandardCharsets.UTF_8);
    }

    @Override
    public @NotNull JoernAnalysisResult analyze(@NotNull CompositionInformation compositionInformation)
            throws IOException, AnalyzerFailureException, InterruptedException {
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
            findings.addAll(this.parseFindings(outFile, compositionInformation.getPresenceConditionMapper(),
                    compositionInformation.getSourceMap()));
        }
        JoernAnalysisResult result = new JoernAnalysisResult(findings, compositionInformation.getEnabledFeatures());
        if (this.resultAggregator != null) {
            this.resultAggregator.addResult(result);
        }
        LOGGER.info("Analysis finished");
        return result;
    }

    private void analyzeWithLanguageInformation(@NotNull LanguageInformation languageInformation,
                                                @NotNull Path sourceLocation, @NotNull Path outFile)
            throws AnalyzerFailureException, IOException, InterruptedException {
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
            throws IOException, AnalyzerFailureException, InterruptedException {
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
            joernProcess.destroy();
            throw e;
        }
        stdoutGobbler.waitFor();
        stderrGobbler.waitFor();
        if (joernExitCode != 0)
            throw new AnalyzerFailureException(String.format("joern exited with %d", joernExitCode));
    }

    private @NotNull List<JoernFinding> parseFindings(@NotNull Path findingsFile,
                                                      @NotNull PresenceConditionMapper presenceConditionMapper,
                                                      @NotNull SourceMap sourceMap)
            throws IOException, AnalyzerFailureException {
        ObjectMapper objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        List<ParsedFinding> finding = objectMapper.readValue(findingsFile.toFile(), new TypeReference<>() {
        });
        List<JoernFinding> list = new ArrayList<>();
        for (ParsedFinding parsedFinding : finding) {
            JoernFinding joernFinding = parsedFinding.toJoernFinding(presenceConditionMapper, sourceMap);
            list.add(joernFinding);
        }
        return list;
    }

    protected record ParsedFinding(@Nullable String name, @Nullable String title, @Nullable String description,
                                   double score, @Nullable List<ParsedEvidence> evidence) {
        public @NotNull JoernFinding toJoernFinding(@NotNull PresenceConditionMapper presenceConditionMapper,
                                                    @NotNull SourceMap sourceMap) throws AnalyzerFailureException {
            if (this.name == null || this.title == null || this.description == null || this.evidence == null)
                throw new AnalyzerFailureException("Joern output is missing required fields");
            Set<SourceLocation> evidenceAsSourceLocation = new HashSet<>();
            for (ParsedEvidence parsedEvidence : this.evidence) {
                SourceLocation parsedEvidenceSourceLocation = parsedEvidence.toSourceLocation();
                evidenceAsSourceLocation.add(parsedEvidenceSourceLocation);
            }
            SourceLocation evidenceForConditionCalculation = evidenceAsSourceLocation.size() == 1
                    ? evidenceAsSourceLocation.iterator().next()
                    : null;
            Node condition = evidenceForConditionCalculation == null
                    ? null
                    : presenceConditionMapper.getPresenceCondition(evidenceForConditionCalculation.file(),
                            evidenceForConditionCalculation.line())
                    .orElse(null);
            Set<SourceLocation> originalLocations = evidenceAsSourceLocation.stream()
                    .map(location -> sourceMap.getOriginalLocation(location).orElse(null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            return new JoernFinding(this.name, this.title, this.description, this.score, originalLocations, condition);
        }
    }

    protected record ParsedEvidence(@Nullable String filename, int lineNumber) {
        public @NotNull SourceLocation toSourceLocation() throws AnalyzerFailureException {
            if (this.filename == null)
                throw new AnalyzerFailureException("File name missing in evidence");
            Path file;
            try {
                file = Path.of(this.filename);
            } catch (InvalidPathException e) {
                throw new AnalyzerFailureException("Invalid file name in evidence", e);
            }
            return new SourceLocation(file, this.lineNumber);
        }
    }
}
