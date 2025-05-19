package edu.kit.varijoern.composers.kconfig.subjects;

import edu.kit.varijoern.composers.ComposerException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.regex.Pattern;

public class LinuxStrategy extends ComposerStrategy {
    private static final Pattern OPTION_NAME_VALUE_PATTERN = Pattern.compile("CONFIG_(\\w+)=.*");
    private static final Pattern OPTION_NOT_SET_PATTERN = Pattern.compile("# CONFIG_(\\w+) is not set");
    private static final String ENABLED_OPTION_FORMAT_STRING = "CONFIG_%s=y";
    private static final String DISABLED_OPTION_FORMAT_STRING = "# CONFIG_%s is not set";

    /**
     * Creates a new {@link BusyboxStrategy} with the specified parameters.
     *
     * @param tmpSourcePath                   the path to the composer's original source directory
     * @param composerTmpPath                 the path to the temporary directory of the composer
     * @param skipPresenceConditionExtraction whether to skip presence condition extraction
     * @param encoding                        the encoding of the subject system's source files
     */
    public LinuxStrategy(@NotNull Path tmpSourcePath, @NotNull Path composerTmpPath,
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
    protected @NotNull String getEnabledOptionFormatString() {
        return ENABLED_OPTION_FORMAT_STRING;
    }

    @Override
    protected @NotNull String getDisabledOptionFormatString() {
        return DISABLED_OPTION_FORMAT_STRING;
    }

    @Override
    public void processWrittenConfig() throws ComposerException, IOException, InterruptedException {
        this.runMake("oldconfig");
    }

    @Override
    public void prepareDependencyDetection() {
        // Nothing to do
    }
}
