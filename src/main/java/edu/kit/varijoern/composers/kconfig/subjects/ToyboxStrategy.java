package edu.kit.varijoern.composers.kconfig.subjects;

import edu.kit.varijoern.composers.ComposerException;
import jodd.io.StreamGobbler;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

public class ToyboxStrategy extends ComposerStrategy {
    private static final Pattern OPTION_NAME_VALUE_PATTERN = Pattern.compile("CONFIG_(\\w+)=.*");
    private static final Pattern OPTION_NOT_SET_PATTERN = Pattern.compile("# CONFIG_(\\w+) is not set");
    private static final String ENABLED_OPTION_FORMAT_STRING = "CONFIG_%s=y";
    private static final String DISABLED_OPTION_FORMAT_STRING = "# CONFIG_%s is not set";

    /**
     * Creates a new {@link ToyboxStrategy} with the specified parameters.
     *
     * @param tmpSourcePath                   the path to the composer's original source directory
     * @param composerTmpPath                 the path to the temporary directory of the composer
     * @param skipPresenceConditionExtraction whether to skip presence condition extraction
     * @param encoding                        the encoding of the subject system's source files
     */
    public ToyboxStrategy(@NotNull Path tmpSourcePath, @NotNull Path composerTmpPath,
                          boolean skipPresenceConditionExtraction, @NotNull Charset encoding) {
        super(tmpSourcePath, composerTmpPath, skipPresenceConditionExtraction, encoding);
    }

    @Override
    public void clean() throws ComposerException, IOException, InterruptedException {
        this.runMake("distclean");
    }

    @Override
    public void generateDefConfig() throws ComposerException, IOException, InterruptedException {
        this.runMake("defconfig");
    }

    @Override
    public @NotNull Path getConfigPath() {
        return this.getTmpSourcePath().resolve(".config");
    }

    @Override
    public @NotNull Pattern getOptionNameValuePattern() {
        return OPTION_NAME_VALUE_PATTERN;
    }

    @Override
    public @NotNull Pattern getOptionNotSetPattern() {
        return OPTION_NOT_SET_PATTERN;
    }

    @Override
    protected @NotNull String getDisabledOptionFormatString() {
        return DISABLED_OPTION_FORMAT_STRING;
    }

    @Override
    protected @NotNull String getEnabledOptionFormatString() {
        return ENABLED_OPTION_FORMAT_STRING;
    }

    @Override
    public void processWrittenConfig() throws ComposerException, IOException, InterruptedException {
        this.runMake("oldconfig");
    }

    @Override
    public void prepareDependencyDetection() throws ComposerException, IOException, InterruptedException {
        Process sedProcess = new ProcessBuilder("sh", "-c",
                // Instead of building, only print the cc commands that would be executed
                "sed -i 's/do_loudly \\$BUILD -c \\$i -o \\$OUT &/echo \\$BUILD -c \\$i -o \\$OUT/g' scripts/make.sh"
                        // The ratelimit function breaks because the echo commands we added are not executed in the
                        // background
                        + " && sed -i 's/ratelimit .*/:/g' scripts/make.sh"
                        // The linker would fail because the object files it expects are not present.
                        // Since no other interesting steps are executed afterward, exit early.
                        + " && sed -i 's/do_loudly \\$BUILD \\$LNKFILES.*/exit 0/g' scripts/make.sh")
                .directory(this.getTmpSourcePath().toFile())
                .start();

        StreamGobbler stdoutGobbler = new StreamGobbler(sedProcess.getInputStream(), STREAM_LOGGER);
        StreamGobbler stderrGobbler = new StreamGobbler(sedProcess.getErrorStream(), STREAM_LOGGER);
        stdoutGobbler.start();
        stderrGobbler.start();

        int sedExitCode;
        try {
            sedExitCode = sedProcess.waitFor();
        } catch (InterruptedException e) {
            sedProcess.destroy();
            throw e;
        }

        if (sedExitCode != 0) {
            throw new ComposerException("Failed to patch make.sh: sed command failed with exit code " + sedExitCode);
        }
    }

    @Override
    public List<String> getDependencyDetectionMakeArgs() {
        // For other subjects, `-in` would be passed to make to prevent it from building anything.
        // Toybox is special in that it delegates the build to a shell script (`make.sh`).
        // In the `prepareDependencyDetection` method, we patched this script to echo the commands
        // instead of executing them. Therefore, we do not need to pass `-in` to make.
        return List.of();
    }
}
