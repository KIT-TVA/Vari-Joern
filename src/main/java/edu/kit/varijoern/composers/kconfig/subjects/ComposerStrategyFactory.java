package edu.kit.varijoern.composers.kconfig.subjects;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;
import java.nio.file.Path;

/**
 * This interface defines a factory for creating {@link ComposerStrategy} instances. It is mainly intended to simplify
 * testing, because the same {@link ComposerStrategy} implementation is often used for different test setups.
 */
public interface ComposerStrategyFactory {
    /**
     * Creates a new {@link ComposerStrategy} instance with the specified parameters.
     *
     * @param tmpSourcePath                   the path to the composer's original source directory
     * @param composerTmpPath                 the path to the temporary directory of the composer
     * @param skipPresenceConditionExtraction whether to skip presence condition extraction
     * @param encoding                        the encoding of the subject system's source files
     */
    @NotNull ComposerStrategy createComposerStrategy(@NotNull Path tmpSourcePath, @NotNull Path composerTmpPath,
                                                     boolean skipPresenceConditionExtraction,
                                                     @NotNull Charset encoding);
}
