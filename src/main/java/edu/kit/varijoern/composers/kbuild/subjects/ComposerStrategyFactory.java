package edu.kit.varijoern.composers.kbuild.subjects;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public interface ComposerStrategyFactory {
    @NotNull ComposerStrategy createComposerStrategy(@NotNull Path tmpSourcePath);
}
