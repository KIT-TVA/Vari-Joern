package edu.kit.varijoern.composers.kbuild.subjects;

import edu.kit.varijoern.composers.ComposerException;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public class FiascoStrategy extends ComposerStrategy {
    private static final Pattern OPTION_NAME_VALUE_PATTERN = Pattern.compile("CONFIG_(\\w+)=.*");
    private static final Pattern OPTION_NOT_SET_PATTERN = Pattern.compile("# CONFIG_(\\w+) is not set");
    private static final String ENABLED_OPTION_FORMAT_STRING = "CONFIG_%s=y";
    private static final String DISABLED_OPTION_FORMAT_STRING = "# CONFIG_%s is not set";

    public FiascoStrategy(Path tmpSourcePath) {
        super(tmpSourcePath);
    }

    @Override
    public void clean() throws ComposerException, IOException, InterruptedException {
        // build/source is usually a symlink. During copying, it is converted to a directory. `make purge`
        // assumes that it can delete it non-recursively with `rm`, which fails. We have to delete it manually.
        FileUtils.deleteDirectory(this.getTmpSourcePath().resolve("build/source").toFile());
        this.runMake("purge");
    }

    @Override
    public void prepare() throws ComposerException, IOException, InterruptedException {
        // Nothing to do
    }

    @Override
    public void generateDefConfig() throws ComposerException, IOException, InterruptedException {
        // Fiasco does not have a `defconfig` command. We have to clean the build directory and run
        // `olddefconfig` instead.
        Files.deleteIfExists(this.getTmpSourcePath().resolve("build/globalconfig.out"));
        this.runMake("olddefconfig");
    }

    @Override
    public @NotNull Path getConfigPath() {
        return this.getTmpSourcePath().resolve("build/globalconfig.out");
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
        this.runMake("olddefconfig");
    }

    @Override
    public void prepareDependencyDetection() throws ComposerException, IOException, InterruptedException {
        // Fiasco uses a second preprocessor for its C++ files. We have to call it before we determine the
        // dependencies without running actual commands.
        LOGGER.info("Running fiasco's C++ preprocessor");
        this.runMake("-C", "build", "create-sources");
    }
}
