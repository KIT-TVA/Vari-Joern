package edu.kit.varijoern.composers.kbuild.subjects;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.composers.ComposerException;
import edu.kit.varijoern.composers.conditionmapping.PresenceConditionMapper;
import edu.kit.varijoern.composers.kbuild.InclusionInformation;
import edu.kit.varijoern.composers.kbuild.conditionmapping.KbuildFilePresenceConditionMapper;
import edu.kit.varijoern.composers.kbuild.conditionmapping.KbuildPresenceConditionMapper;
import edu.kit.varijoern.composers.kbuild.conditionmapping.LinePresenceConditionMapper;
import edu.kit.varijoern.composers.sourcemap.SourceMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class BusyboxStrategy extends ComposerStrategy {
    private static final Pattern OPTION_NAME_VALUE_PATTERN = Pattern.compile("CONFIG_(\\w+)=.*");
    private static final Pattern OPTION_NOT_SET_PATTERN = Pattern.compile("# CONFIG_(\\w+) is not set");
    private static final String ENABLED_OPTION_FORMAT_STRING = "CONFIG_%s=y";
    private static final String DISABLED_OPTION_FORMAT_STRING = "# CONFIG_%s is not set";
    private @Nullable KbuildFilePresenceConditionMapper filePresenceConditionMapper;

    public BusyboxStrategy(@NotNull Path tmpSourcePath, @NotNull Path composerTmpPath,
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
    public void prepareDependencyDetection() throws ComposerException, IOException, InterruptedException {
        // We have to run `make applets` to generate several header files.
        this.runMake("applets");
        // `make applets` generates `applets/applets.o`, which is part of the final BusyBox executables and would
        // break dependency detection. We have to delete it.
        Files.delete(this.getTmpSourcePath().resolve("applets/applets.o"));
    }

    @Override
    public void beforeComposition(IFeatureModel featureModel)
            throws ComposerException, IOException, InterruptedException {
        if (this.filePresenceConditionMapper != null) {
            return;
        }

        if (this.shouldSkipPresenceConditionExtraction()) {
            LOGGER.info("Skipping file presence condition extraction");
            return;
        }

        LOGGER.info("Creating file presence condition mapper");

        // BusyBox's Kbuild variant allows to specify Kbuild information in the source files. Since kmax cannot
        // handle this, we use `make gen_build_files` to generate Kbuild files.
        this.runMake("gen_build_files");

        this.filePresenceConditionMapper = new KbuildFilePresenceConditionMapper(this.getTmpSourcePath(), "busybox",
                this.getComposerTmpPath(), featureModel);
    }

    @Override
    public @NotNull PresenceConditionMapper createPresenceConditionMapper(
            Stream<Map.Entry<Path, InclusionInformation>> inclusionInformation, @NotNull SourceMap sourceMap,
            @NotNull Set<String> knownFeatures) throws ComposerException, IOException, InterruptedException {
        LOGGER.info("Creating line presence condition mappers");

        this.clean();

        Map<Path, LinePresenceConditionMapper> linePresenceConditionMappers = new HashMap<>();
        Iterator<Map.Entry<Path, InclusionInformation>> inclusionInformationIterator = inclusionInformation.iterator();

        while (inclusionInformationIterator.hasNext()) {
            Map.Entry<Path, InclusionInformation> entry = inclusionInformationIterator.next();
            Map.Entry<Path, LinePresenceConditionMapper> busybox = Map.entry(entry.getKey(),
                    new LinePresenceConditionMapper(entry.getValue(),
                            this.getTmpSourcePath(), knownFeatures, "busybox", this.getEncoding())
            );
            linePresenceConditionMappers.put(busybox.getKey(), busybox.getValue());
        }

        return new KbuildPresenceConditionMapper(Objects.requireNonNull(this.filePresenceConditionMapper),
                linePresenceConditionMappers, sourceMap);
    }
}
