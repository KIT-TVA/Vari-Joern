package edu.kit.varijoern.samplers;

import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.FeatureModelCNF;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.analyzers.AnalysisResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

/**
 * This sampler chooses a sample of configuration using local search to approximate t-wise coverage.
 */
public class LSSamplingPlusSampler extends DimacsSampler {
    public static final String NAME = "ls-sampling-plus";

    private static final String LS_DIR = "/ls";
    private static final String LS_INPUT_FILE_NAME = "model.cnf";
    private static final String LS_OUTPUT_FILE = LS_DIR + "/model_testcase_set.txt";

    private final int sampleSize;
    private final int t;
    private final int lambda;
    private final int delta;

    /**
     * Creates a new {@link LSSamplingPlusSampler} which generates samples for the specified feature model.
     *
     * @param featureModel the feature model
     * @param sampleSize   the number of configurations to be generated
     * @param t            the t-wise coverage to aim for
     * @param lambda       the number of candidates per iteration
     * @param delta        the cardinality of the measuring set
     */
    public LSSamplingPlusSampler(@NotNull IFeatureModel featureModel, int sampleSize, int t, int lambda, int delta) {
        super(featureModel);
        this.sampleSize = sampleSize;
        this.t = t;
        this.lambda = lambda;
        this.delta = delta;
    }

    @Override
    public @NotNull List<Map<String, Boolean>> sample(@Nullable List<AnalysisResult<?>> analysisResults,
                                                      @NotNull Path tmpPath)
            throws SamplerException, InterruptedException, IOException {
        LOGGER.info("Calculating local search sample");
        CNF cnf = FeatureModelCNF.fromFeatureModel(this.featureModel);
        Path inputFile = tmpPath.resolve(LS_INPUT_FILE_NAME);
        this.writeDimacsFile(inputFile, cnf);
        Random random = new Random();
        ProcessBuilder processBuilder = new ProcessBuilder(
                "./LS-Sampling-Plus", "-input_cnf_path", inputFile.toString(),
                "-seed", Integer.toString(random.nextInt()),
                "-k", Integer.toString(this.sampleSize),
                "-t_wise", Integer.toString(this.t),
                "-lambda", Integer.toString(this.lambda),
                "-delta", Integer.toString(this.delta)
        );
        processBuilder.directory(new File(LS_DIR));
        int exitCode = this.runSamplerProcess(processBuilder);
        if (exitCode != 0) {
            throw new SamplerException("LS-Sampling-Plus exited with code " + exitCode);
        }
        Path lsSamplingPlusOutputFile = Paths.get(LS_OUTPUT_FILE);
        List<Map<String, Boolean>> result = parseLSSamplingPlusOutput(lsSamplingPlusOutputFile, cnf);
        LOGGER.info("Generated {} configurations", result.size());
        return result;
    }

    private @NotNull List<Map<String, Boolean>> parseLSSamplingPlusOutput(Path lsSamplingPlusOutputFile, CNF cnf)
            throws IOException {
        try (Stream<String> lines = Files.lines(lsSamplingPlusOutputFile)) {
            return lines.map(line -> literalsToConfigurationNoIndexes(line.split(" "), cnf)).toList();
        }
    }
}
