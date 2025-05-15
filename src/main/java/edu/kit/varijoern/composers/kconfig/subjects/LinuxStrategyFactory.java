package edu.kit.varijoern.composers.kconfig.subjects;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;
import java.nio.file.Path;

public class LinuxStrategyFactory implements ComposerStrategyFactory {
    @Override
    public @NotNull ComposerStrategy createComposerStrategy(@NotNull Path tmpSourcePath,
                                                            @NotNull Path composerTmpPath,
                                                            boolean skipPresenceConditionExtraction,
                                                            @NotNull Charset encoding) {
        return new LinuxStrategy(tmpSourcePath, composerTmpPath, skipPresenceConditionExtraction, encoding);
    }
}
