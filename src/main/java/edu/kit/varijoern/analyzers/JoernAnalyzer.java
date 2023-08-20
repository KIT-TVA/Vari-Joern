package edu.kit.varijoern.analyzers;

import jodd.io.StreamGobbler;

import java.io.IOException;
import java.nio.file.Path;

public class JoernAnalyzer implements Analyzer {
    public static final String NAME = "joern";
    private final String command;
    private final Path workspacePath;

    /**
     * Creates a new {@link JoernAnalyzer} instance which uses the specified command name to run joern-scan.
     *
     * @param command       the path to or name of the joern-scan command
     * @param workspacePath the directory to use for Joern's workspace folder
     */
    public JoernAnalyzer(String command, Path workspacePath) {
        this.command = command;
        this.workspacePath = workspacePath;
    }

    @Override
    public AnalysisResult analyze(Path sourceLocation) throws IOException, AnalyzerFailureException {
        Process joernProcess = Runtime.getRuntime().exec(
            new String[]{this.command, "--overwrite", sourceLocation.toString()},
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
            throw new AnalyzerFailureException(String.format("joern-scan exited with %d", joernExitCode));
        return null; // TODO: parse results and return them
    }
}
