package edu.kit.varijoern.composers.kbuild.subjects;

import edu.kit.varijoern.composers.ComposerException;
import jodd.io.StreamGobbler;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.io.IoBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public abstract class ComposerStrategy {
    protected static final Logger LOGGER = LogManager.getLogger();
    protected static final OutputStream STREAM_LOGGER = IoBuilder.forLogger().setLevel(Level.DEBUG).buildOutputStream();

    private final Path tmpSourcePath;

    protected ComposerStrategy(Path tmpSourcePath) {
        this.tmpSourcePath = tmpSourcePath;
    }

    protected Path getTmpSourcePath() {
        return this.tmpSourcePath;
    }

    public abstract void clean() throws ComposerException, IOException, InterruptedException;

    public abstract void prepare() throws ComposerException, IOException, InterruptedException;

    public abstract void generateDefConfig() throws ComposerException, IOException, InterruptedException;

    public abstract @NotNull Path getConfigPath();

    public abstract @NotNull Pattern getOptionNameValuePattern();

    public abstract @NotNull Pattern getOptionNotSetPattern();

    protected abstract @NotNull String getDisabledOptionFormatString();

    protected abstract @NotNull String getEnabledOptionFormatString();

    /**
     * Creates a line for a .config file containing the specified option.
     *
     * @param optionName the name of the option
     * @param activated  whether the option is activated
     * @return the line that can be used in a .config file
     */
    public @NotNull String formatOption(@NotNull String optionName, boolean activated) {
        if (activated) {
            return this.getEnabledOptionFormatString().formatted(optionName);
        } else {
            return this.getDisabledOptionFormatString().formatted(optionName);
        }
    }

    public abstract void processWrittenConfig() throws ComposerException, IOException, InterruptedException;

    public abstract void prepareDependencyDetection() throws ComposerException, IOException, InterruptedException;
    /**
     * A helper function to run make with the specified arguments with the temporary source directory as the working
     * directory.
     *
     * @param args the arguments to pass to make
     * @throws ComposerException    if make returns a non-zero exit code
     * @throws IOException          if an I/O error occurs
     * @throws InterruptedException if the current thread is interrupted
     */
    protected void runMake(String @NotNull ... args) throws ComposerException, IOException, InterruptedException {
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
}
