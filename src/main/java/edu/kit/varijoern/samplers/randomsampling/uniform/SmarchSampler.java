package edu.kit.varijoern.samplers.randomsampling.uniform;

import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.FeatureModelCNF;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.analyzers.AnalysisResult;
import edu.kit.varijoern.samplers.DimacsSampler;
import edu.kit.varijoern.samplers.SamplerException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * This sampler chooses a sample of configurations uniformly at random using Smarch.
 */
public class SmarchSampler extends DimacsSampler {
    public static final String NAME = "smarch";
    private static final String SMARCH_OUTPUT_DIR = "smarch";
    private static final String SMARCH_INPUT_FILE = "model.dimacs";
    private static final String SMARCH_OUTPUT_FILE_PATTERN = "model_%d.samples";

    // Smarch parameters.
    private final int sampleSize;

    /**
     * Creates a new {@link SmarchSampler} which generates samples for the specified feature model (expressed as
     * {@link IFeatureModel}).
     *
     * @param featureModel the feature model (expressed as {@link IFeatureModel}).
     * @param sampleSize   the number of configurations to be generated.
     */
    public SmarchSampler(@NotNull IFeatureModel featureModel, int sampleSize) {
        super(featureModel);
        this.sampleSize = sampleSize;
    }

    @Override
    public @NotNull List<Map<String, Boolean>> sample(@Nullable List<AnalysisResult<?>> analysisResults,
                                                      @NotNull Path tmpPath)
            throws SamplerException, InterruptedException, IOException {
        LOGGER.info("Calculating uniform sample");

        // Transform feature model to CNF and then the CNF to DIMACS.
        CNF cnf = FeatureModelCNF.fromFeatureModel(this.featureModel);
        this.writeDimacsFile(tmpPath.resolve(SmarchSampler.SMARCH_INPUT_FILE), cnf);

        // Configure ProcessBuilder.
        Path smarchOutputDir = tmpPath.resolve(SmarchSampler.SMARCH_OUTPUT_DIR);
        ProcessBuilder processBuilder = new ProcessBuilder("smarch_opt",
                "-o", smarchOutputDir.toString(),
                "-p", String.valueOf(Runtime.getRuntime().availableProcessors()),
                tmpPath.resolve("model.dimacs").toString(), Integer.toString(this.sampleSize));

        // Execute Smarch.
        int exitCode = this.runSamplerProcess(processBuilder);
        if (exitCode != 0) {
            throw new SamplerException(String.format("Smarch exited with code %d", exitCode));
        }

        Path smarchOutputFile = smarchOutputDir.resolve(String.format(SMARCH_OUTPUT_FILE_PATTERN, this.sampleSize));
        List<Map<String, Boolean>> result = parseSmarchOutput(smarchOutputFile, cnf);
        LOGGER.info("Sampled {} configurations using Smarch.", result.size());
        return result;
    }

    /**
     * Parse Smarch output into a sample, i.e., a {@link List} of {@link Map}s mapping features to their
     * selection status.
     *
     * @param smarchOutputFile the {@link Path} at which the output by Smarch can be found.
     * @param cnf              the {@link CNF} of the feature model used to translate the feature literals returned by
     *                         Smarch into their correct feature names.
     * @return the sample created by Smarch represented as a {@link List} of {@link Map}s mapping features to
     * their selection status.
     * @throws IOException if an I/O error occurs opening the file.
     */
    private @NotNull List<Map<String, Boolean>> parseSmarchOutput(@NotNull Path smarchOutputFile,
                                                                  @NotNull CNF cnf) throws IOException {
        try (Stream<String> lines = Files.lines(smarchOutputFile)) {
            return lines.map(line -> this.literalsToConfiguration(line.split(","), cnf)).toList();
        }
    }
}
