package edu.kit.varijoern.composers.kbuild.subjects;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;
import java.nio.file.Path;

public class BusyboxStrategyFactory implements ComposerStrategyFactory {
    @Override
    public @NotNull ComposerStrategy createComposerStrategy(@NotNull Path tmpSourcePath,
                                                            @NotNull Path composerTmpPath,
                                                            boolean skipPresenceConditionExtraction,
                                                            @NotNull Charset charset) {
        return new BusyboxStrategy(tmpSourcePath, composerTmpPath, skipPresenceConditionExtraction, charset);
    }
}
