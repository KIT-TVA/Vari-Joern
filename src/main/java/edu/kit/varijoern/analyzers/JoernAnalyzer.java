package edu.kit.varijoern.analyzers;

import jodd.io.StreamGobbler;
import jodd.util.ResourcesUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

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
    public AnalysisResult analyze(Path sourceLocation) throws IOException, AnalyzerFailureException {
        Path outFile = this.workspacePath.resolve(
            String.format("%s-%s.json",
                sourceLocation.subpath(sourceLocation.getNameCount() - 1, sourceLocation.getNameCount()),
                UUID.randomUUID()
            )
        );
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
        return null; // TODO: parse results and return them
    }
}
