package edu.kit.varijoern.analyzers.joern;

import edu.kit.varijoern.analyzers.AnalyzerFailureException;
import edu.kit.varijoern.composers.CCPPLanguageInformation;
import edu.kit.varijoern.composers.GenericLanguageInformation;
import edu.kit.varijoern.composers.LanguageInformation;
import edu.kit.varijoern.composers.LanguageInformationVisitor;
import jodd.io.StreamGobbler;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.io.IoBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This visitor generates code property graphs using {@code joern-parse} for each language information it visits.
 */
public class CPGGenerationVisitor extends LanguageInformationVisitor<AnalyzerFailureException> {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final OutputStream STREAM_LOGGER = IoBuilder.forLogger().setLevel(Level.DEBUG).buildOutputStream();
    private final @NotNull Path inputDirectory;
    private final @NotNull Path outputFile;
    private final @Nullable Path joernPath;

    /**
     * Creates a new {@link CPGGenerationVisitor} instance.
     *
     * @param inputDirectory the path containing the source code. Must be absolute.
     * @param outputFile     the file to write the CPG to. Must be absolute.
     * @param joernPath      the directory in which the Joern executables are stored. May be null to use the system
     *                       PATH.
     */
    public CPGGenerationVisitor(@NotNull Path inputDirectory, @NotNull Path outputFile, @Nullable Path joernPath) {
        this.inputDirectory = inputDirectory;
        this.outputFile = outputFile;
        this.joernPath = joernPath;
        if (!inputDirectory.isAbsolute())
            throw new IllegalArgumentException("Input directory must be absolute");
        if (!outputFile.isAbsolute())
            throw new IllegalArgumentException("Output file must be absolute");
    }

    @Override
    protected void visitUnimplemented(@NotNull LanguageInformation languageInformation) {
        LOGGER.warn("Language {} is not supported by the analyzer.", languageInformation.getName());
    }

    @Override
    public void visit(@NotNull GenericLanguageInformation languageInformation) throws AnalyzerFailureException {
        generateCPG(List.of());
    }

    @Override
    public void visit(@NotNull CCPPLanguageInformation languageInformation) throws AnalyzerFailureException {
        List<String> extraArguments = new ArrayList<>();
        extraArguments.add("--language");
        extraArguments.add("newc");
        extraArguments.add("--frontend-args");
        Set<String> includePaths = languageInformation.getIncludePaths().values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
        for (String includePath : includePaths) {
            extraArguments.add("--include");
            extraArguments.add(includePath);
        }
        generateCPG(extraArguments);
    }

    private void generateCPG(@NotNull List<String> joernParseExtraArguments) throws AnalyzerFailureException {
        List<String> command = Stream.concat(
                Stream.of(this.joernPath == null
                                ? "joern-parse"
                                : this.joernPath.resolve("joern-parse").toString(),
                        this.inputDirectory.toString(),
                        "-o", this.outputFile.toString()
                ),
                joernParseExtraArguments.stream()
        ).toList();
        Process parserProcess;
        try {
            parserProcess = new ProcessBuilder(command).start();
        } catch (IOException e) {
            throw new AnalyzerFailureException("Failed to parse source code", e);
        }
        StreamGobbler stdoutGobbler = new StreamGobbler(parserProcess.getInputStream(), STREAM_LOGGER);
        StreamGobbler stderrGobbler = new StreamGobbler(parserProcess.getErrorStream(), STREAM_LOGGER);
        stdoutGobbler.start();
        stderrGobbler.start();
        int exitCode;
        try {
            exitCode = parserProcess.waitFor();
        } catch (InterruptedException e) {
            throw new AnalyzerFailureException("joern-parse was interrupted", e);
        }
        if (exitCode != 0)
            throw new AnalyzerFailureException("joern-parse failed with exit code " + exitCode);
    }
}
