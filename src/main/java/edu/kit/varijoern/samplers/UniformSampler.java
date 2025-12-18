package edu.kit.varijoern.samplers;

import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.FeatureModelCNF;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.analyzers.AnalysisResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * This sampler chooses a sample of configurations uniformly at random.
 */
public class UniformSampler extends DimacsSampler {
    public static final String NAME = "uniform";

    private static final String SMARCH_OUTPUT_DIR = "smarch";
    private static final String SMARCH_INPUT_FILE = "model.dimacs";
    private static final String SMARCH_OUTPUT_FILE_PATTERN = "model_%d.samples";

    private final int sampleSize;

    /**
     * Creates a new {@link UniformSampler} which generates samples for the specified feature model.
     *
     * @param featureModel the feature model
     * @param sampleSize   the number of configurations to be generated
     */
    public UniformSampler(@NotNull IFeatureModel featureModel, int sampleSize) {
        super(featureModel);
        this.sampleSize = sampleSize;
    }

    @Override
    public @NotNull List<Map<String, Boolean>> sample(@Nullable List<AnalysisResult<?>> analysisResults,
                                                      @NotNull Path tmpPath)
            throws SamplerException, InterruptedException, IOException {
        LOGGER.info("Calculating uniform sample");
        CNF cnf = FeatureModelCNF.fromFeatureModel(this.featureModel);
        this.writeDimacsFile(tmpPath.resolve(SMARCH_INPUT_FILE), cnf);

        Path smarchOutputDir = tmpPath.resolve(SMARCH_OUTPUT_DIR);
        ProcessBuilder processBuilder = new ProcessBuilder("smarch_opt",
                "-o", smarchOutputDir.toString(),
                "-p", String.valueOf(Runtime.getRuntime().availableProcessors()),
                tmpPath.resolve("model.dimacs").toString(), Integer.toString(this.sampleSize));
        int exitCode = this.runSamplerProcess(processBuilder);
        if (exitCode != 0) {
            throw new SamplerException("smarch_opt exited with code " + exitCode);
        }

        Path smarchOutputFile = smarchOutputDir.resolve(String.format(SMARCH_OUTPUT_FILE_PATTERN, this.sampleSize));
        List<Map<String, Boolean>> result = parseSmarchOutput(smarchOutputFile, cnf);

        LOGGER.info("Generated {} configurations", result.size());
        return result;
    }

    private @NotNull List<Map<String, Boolean>> parseSmarchOutput(Path smarchOutputFile, CNF cnf) throws IOException {
        try (Stream<String> lines = Files.lines(smarchOutputFile)) {
            return lines.map(line -> this.literalsToConfiguration(line.split(","), cnf)).toList();
        }
    }
}
