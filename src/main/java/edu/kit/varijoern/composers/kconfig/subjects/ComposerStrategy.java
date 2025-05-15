package edu.kit.varijoern.composers.kconfig.subjects;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.composers.ComposerException;
import edu.kit.varijoern.composers.conditionmapping.PresenceConditionMapper;
import edu.kit.varijoern.composers.conditionmapping.EmptyPresenceConditionMapper;
import edu.kit.varijoern.composers.kconfig.InclusionInformation;
import edu.kit.varijoern.composers.sourcemap.SourceMap;
import jodd.io.StreamGobbler;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.io.IoBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Abstract class for the composer strategies. Implementers should provide code for composing a specific subject system.
 */
public abstract class ComposerStrategy {
    protected static final Logger LOGGER = LogManager.getLogger();
    protected static final OutputStream STREAM_LOGGER = IoBuilder.forLogger().setLevel(Level.DEBUG).buildOutputStream();

    private final @NotNull Path tmpSourcePath;
    private final @NotNull Path composerTmpPath;
    private final boolean skipPresenceConditionExtraction;
    private final @NotNull Charset encoding;

    /**
     * Creates a new {@link ComposerStrategy} with the specified parameters.
     *
     * @param tmpSourcePath                   the path to the composer's original source directory
     * @param composerTmpPath                 the path to the temporary directory of the composer
     * @param skipPresenceConditionExtraction whether to skip presence condition extraction
     * @param encoding                        the encoding of the subject system's source files
     */
    protected ComposerStrategy(@NotNull Path tmpSourcePath, @NotNull Path composerTmpPath,
                               boolean skipPresenceConditionExtraction, @NotNull Charset encoding) {
        this.tmpSourcePath = tmpSourcePath;
        this.composerTmpPath = composerTmpPath;
        this.skipPresenceConditionExtraction = skipPresenceConditionExtraction;
        this.encoding = encoding;
    }

    /**
     * Returns the path to the composer's original source directory.
     *
     * @return the path to the composer's original source directory
     */
    protected @NotNull Path getTmpSourcePath() {
        return this.tmpSourcePath;
    }

    /**
     * Returns the path to the temporary directory of the composer.
     *
     * @return the path to the temporary directory of the composer
     */
    protected @NotNull Path getComposerTmpPath() {
        return this.composerTmpPath;
    }

    /**
     * Returns whether to skip presence condition extraction.
     *
     * @return true if presence condition extraction should be skipped, false otherwise
     */
    protected boolean shouldSkipPresenceConditionExtraction() {
        return skipPresenceConditionExtraction;
    }

    /**
     * Returns the encoding of the subject system's source files.
     *
     * @return the encoding of the subject system's source files
     */
    protected @NotNull Charset getEncoding() {
        return this.encoding;
    }

    /**
     * Cleans up artifacts that might be present in the original source directory. This is usually done by running
     * `make clean`, depending on the subject system. Leftover artifacts can cause problems with the process of
     * determining the files included in the composed variant.
     *
     * @throws ComposerException    if an error occurs during the cleanup
     * @throws IOException          if an I/O error occurs
     * @throws InterruptedException if the current thread is interrupted
     */
    public abstract void clean() throws ComposerException, IOException, InterruptedException;

    /**
     * Called before the composition starts. This is a good place to extract presence conditions of files, if not
     * already done in a previous composition process.
     *
     * @param featureModel the feature model that will be used for the composition
     * @throws ComposerException    if an error occurs during the preparation
     * @throws InterruptedException if the current thread is interrupted
     */
    public void beforeComposition(IFeatureModel featureModel)
            throws ComposerException, IOException, InterruptedException {
    }

    /**
     * Generates the default configuration for the subject system. This is usually done by running `make defconfig`,
     * depending on the subject system.
     *
     * @throws ComposerException    if an error occurs during the generation
     * @throws IOException          if an I/O error occurs
     * @throws InterruptedException if the current thread is interrupted
     */
    public abstract void generateDefConfig() throws ComposerException, IOException, InterruptedException;

    /**
     * Returns the path to the configuration file of the subject system. This is usually `.config`, but can be
     * different for some systems.
     *
     * @return the path to the configuration file
     */
    public abstract @NotNull Path getConfigPath();

    /**
     * Returns the pattern used to match option names set to a value in the configuration file. This is usually
     * `CONFIG_<option_name>=<value>`, but can be different for some systems. The pattern is expected to contain a
     * capture group for the option name.
     *
     * @return the pattern used to match option names set to a value
     */
    public abstract @NotNull Pattern getOptionNameValuePattern();

    /**
     * Returns the pattern used to match option names that are not set in the configuration file. This is usually
     * `# CONFIG_<option_name> is not set`, but can be different for some systems. The pattern is expected to contain a
     * capture group for the option name.
     *
     * @return the pattern used to match option names that are not set
     */
    public abstract @NotNull Pattern getOptionNotSetPattern();

    /**
     * Returns the format string used to format disabled options in the configuration file. This is usually
     * `# CONFIG_<option_name> is not set`, but can be different for some systems.
     *
     * @return the format string used to format disabled options
     */
    protected abstract @NotNull String getDisabledOptionFormatString();

    /**
     * Returns the format string used to format enabled options in the configuration file. This is usually
     * `CONFIG_<option_name>=y`, but can be different for some systems.
     *
     * @return the format string used to format enabled options
     */
    protected abstract @NotNull String getEnabledOptionFormatString();

    /**
     * Creates a line for a .config file containing the specified option.
     *
     * @param optionName the name of the option
     * @param activated  whether the option is activated
     * @return the line that can be used in a .config file
     */
    public @NotNull String formatOption(@NotNull String optionName, boolean activated) {
        if (activated) {
            return this.getEnabledOptionFormatString().formatted(optionName);
        } else {
            return this.getDisabledOptionFormatString().formatted(optionName);
        }
    }

    /**
     * Kconfig systems usually generate a header file with the configuration options. This file is generated during the
     * configuration process and used for determining which files are included in the composed variant. Typically, this
     * file is named `config.h` and can be generated by running `make oldconfig`, depending on the subject system. This
     * method should be called after the configuration process to process the generated header file.
     *
     * @throws ComposerException    if an error occurs during the processing
     * @throws IOException          if an I/O error occurs
     * @throws InterruptedException if the current thread is interrupted
     */
    public abstract void processWrittenConfig() throws ComposerException, IOException, InterruptedException;

    /**
     * Some Kconfig systems require additional steps before dependency detection can be performed. This method should
     * be called to prepare the system for dependency detection.
     *
     * @throws ComposerException    if an error occurs during the preparation
     * @throws IOException          if an I/O error occurs
     * @throws InterruptedException if the current thread is interrupted
     */
    public abstract void prepareDependencyDetection() throws ComposerException, IOException, InterruptedException;

    /**
     * Creates a {@link PresenceConditionMapper} for the given inclusion information. This method is called after the
     * dependency detection process is finished. The default implementation returns an empty
     * {@link PresenceConditionMapper}. Subclasses can override this method to provide a custom
     * {@link PresenceConditionMapper}.
     *
     * @param inclusionInformation the files that are compiled into the variant and their inclusion information
     * @param sourceMap            the source map that maps the generated files to the original source files
     * @param knownFeatures        the set of all features of the feature model
     * @return a {@link PresenceConditionMapper} for the given inclusion information
     * @throws ComposerException    if an error occurs during the creation of the presence condition mapper
     * @throws IOException          if an I/O error occurs
     * @throws InterruptedException if the current thread is interrupted
     */
    public @NotNull PresenceConditionMapper createPresenceConditionMapper(
            Stream<Map.Entry<Path, InclusionInformation>> inclusionInformation, @NotNull SourceMap sourceMap,
            @NotNull Set<String> knownFeatures) throws ComposerException, IOException, InterruptedException {
        return new EmptyPresenceConditionMapper();
    }

    /**
     * A helper function to run make with the specified arguments with the temporary source directory as the working
     * directory.
     *
     * @param args the arguments to pass to make
     * @throws ComposerException    if make returns a non-zero exit code
     * @throws IOException          if an I/O error occurs
     * @throws InterruptedException if the current thread is interrupted
     */
    protected void runMake(String @NotNull ... args) throws ComposerException, IOException, InterruptedException {
        Process makeProcess = new ProcessBuilder(
                Stream.concat(Stream.of("make"), Arrays.stream(args))
                        .toList())
                .directory(this.tmpSourcePath.toFile())
                .start();
        StreamGobbler stdoutGobbler = new StreamGobbler(makeProcess.getInputStream(), STREAM_LOGGER);
        StreamGobbler stderrGobbler = new StreamGobbler(makeProcess.getErrorStream(), STREAM_LOGGER);
        stdoutGobbler.start();
        stderrGobbler.start();
        int makeExitCode;
        try {
            makeExitCode = makeProcess.waitFor();
        } catch (InterruptedException e) {
            makeProcess.destroy();
            throw e;
        }
        if (makeExitCode != 0)
            throw new ComposerException("Make failed with exit code %d".formatted(makeExitCode));
    }
}
