package edu.kit.varijoern.composers.kbuild.subjects;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;
import java.nio.file.Path;

public interface ComposerStrategyFactory {
    @NotNull ComposerStrategy createComposerStrategy(@NotNull Path tmpSourcePath, @NotNull Path composerTmpPath,
                                                     boolean skipPresenceConditionExtraction, @NotNull Charset charset);
}
