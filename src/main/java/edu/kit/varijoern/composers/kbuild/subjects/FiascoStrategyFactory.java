package edu.kit.varijoern.composers.kbuild.subjects;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public class FiascoStrategyFactory implements ComposerStrategyFactory {
    @Override
    public @NotNull ComposerStrategy createComposerStrategy(@NotNull Path tmpSourcePath) {
        return new FiascoStrategy(tmpSourcePath);
    }
}
