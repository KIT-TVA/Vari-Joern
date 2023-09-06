package edu.kit.varijoern.analyzers.joern;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.kit.varijoern.analyzers.AnalysisResult;
import edu.kit.varijoern.analyzers.Analyzer;
import edu.kit.varijoern.analyzers.AnalyzerFailureException;
import edu.kit.varijoern.composers.CompositionInformation;
import jodd.io.StreamGobbler;
import jodd.util.ResourcesUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * An analyzer that uses Joern.
 */
public class JoernAnalyzer implements Analyzer {
    public static final String NAME = "joern";
    private final String command;
    private final Path workspacePath;
    private final Path scanScriptPath;

    /**
     * Creates a new {@link JoernAnalyzer} instance which uses the specified command name to run joern-scan.
     *
     * @param command       the path to or name of the joern-scan command
     * @param workspacePath the directory to use for Joern's workspace folder
     */
    public JoernAnalyzer(String command, Path workspacePath) throws IOException {
        this.command = command;
        this.workspacePath = workspacePath;

        String scannerScript = ResourcesUtil.getResourceAsString("joern/scan.sc");
        this.scanScriptPath = workspacePath.resolve("scan.sc");
        Files.writeString(this.scanScriptPath, scannerScript, StandardCharsets.UTF_8);
    }

    @Override
    public AnalysisResult analyze(CompositionInformation compositionInformation)
        throws IOException, AnalyzerFailureException {
        Path sourceLocation = compositionInformation.getLocation();
        Path outFile = this.workspacePath.resolve(
            String.format("%s-%s.json",
                sourceLocation.subpath(sourceLocation.getNameCount() - 1, sourceLocation.getNameCount()),
                UUID.randomUUID()
            )
        );
        runJoern(sourceLocation, outFile);
        List<JoernFinding> findings = this.parseFindings(outFile);
        return new JoernAnalysisResult(findings, compositionInformation.getEnabledFeatures());
    }

    /**
     * Runs Joern on the specified source code. The results are saved in the JSON format in the specified file.
     *
     * @param sourceLocation the location of the source code
     * @param outFile        the path of the file to save the findings in
     * @throws IOException              if an I/O error occurred
     * @throws AnalyzerFailureException if Joern exited with a non-zero exit code
     */
    private void runJoern(Path sourceLocation, Path outFile) throws IOException, AnalyzerFailureException {
        Process joernProcess = Runtime.getRuntime().exec(
            new String[]{
                this.command,
                "--script", this.scanScriptPath.toString(),
                "--param", String.format("codePath=%s", sourceLocation),
                "--param", String.format("outFile=%s", outFile)
            },
            null,
            this.workspacePath.toFile()
        );
        StreamGobbler stdoutGobbler = new StreamGobbler(joernProcess.getInputStream(), System.out);
        StreamGobbler stderrGobbler = new StreamGobbler(joernProcess.getErrorStream(), System.err);
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
