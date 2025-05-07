package edu.kit.varijoern.composers.kbuild.subjects;

import edu.kit.varijoern.composers.ComposerException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public class BusyboxStrategy extends ComposerStrategy {
    private static final Pattern OPTION_NAME_VALUE_PATTERN = Pattern.compile("CONFIG_(\\w+)=.*");
    private static final Pattern OPTION_NOT_SET_PATTERN = Pattern.compile("# CONFIG_(\\w+) is not set");
    private static final String ENABLED_OPTION_FORMAT_STRING = "CONFIG_%s=y";
    private static final String DISABLED_OPTION_FORMAT_STRING = "# CONFIG_%s is not set";

    public BusyboxStrategy(Path tmpSourcePath) {
        super(tmpSourcePath);
    }

    @Override
    public void clean() throws ComposerException, IOException, InterruptedException {
        this.runMake("distclean");
    }

    @Override
    public void prepare() throws ComposerException, IOException, InterruptedException {
        // BusyBox's Kbuild variant allows to specify Kbuild information in the source files. Since kmax cannot
        // handle this, we use `make gen_build_files` to generate Kbuild files.
        this.runMake("gen_build_files");
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
    public void prepareDependencyDetection() throws ComposerException, IOException, InterruptedException {
        // We have to run `make applets` to generate several header files.
        this.runMake("applets");
        // `make applets` generates `applets/applets.o`, which is part of the final BusyBox executables and would
        // break dependency detection. We have to delete it.
        Files.delete(this.getTmpSourcePath().resolve("applets/applets.o"));
    }
}
