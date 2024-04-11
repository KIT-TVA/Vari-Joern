package edu.kit.varijoern.analyzers.joern;

import edu.kit.varijoern.analyzers.AnalyzerFailureException;
import edu.kit.varijoern.composers.CCPPLanguageInformation;
import edu.kit.varijoern.composers.GenericLanguageInformation;
import edu.kit.varijoern.composers.LanguageInformation;
import edu.kit.varijoern.composers.LanguageInformationVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
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
    private final Path inputDirectory;
    private final Path outputFile;
    @Nullable
    private final Path joernPath;

    /**
     * Creates a new {@link CPGGenerationVisitor} instance.
     *
     * @param inputDirectory the path containing the source code
     * @param outputFile     the file to write the CPG to
     * @param joernPath      the directory in which the Joern executables are stored
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
    protected void visitUnimplemented(LanguageInformation languageInformation) {
        System.err.printf("Language %s is not supported by the analyzer.%n", languageInformation.getName());
    }

    @Override
    public void visit(GenericLanguageInformation languageInformation) throws AnalyzerFailureException {
        generateCPG(List.of());
    }

    @Override
    public void visit(CCPPLanguageInformation languageInformation) throws AnalyzerFailureException {
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

    private void generateCPG(List<String> joernParseExtraArguments) throws AnalyzerFailureException {
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
            parserProcess = new ProcessBuilder(command).inheritIO().start();
        } catch (IOException e) {
            throw new AnalyzerFailureException("Failed to parse source code", e);
        }
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
