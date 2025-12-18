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
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * This sampler chooses a sample of configurations using weighted random sampling.
 */
public class BaitalSampler extends DimacsSampler {
    public static final String NAME = "baital";

    private static final String BAITAL_INPUT_FILE = "model.cnf";
    private static final String BAITAL_OUTPUT_FILE = "model.samples";

    private final int sampleSize;
    private final int t;
    private final int strategy;
    private final int rounds;
    private final boolean extraSamples;

    /**
     * Creates a new {@link BaitalSampler} which generates samples for the specified feature model.
     *
     * @param featureModel the feature model
     * @param sampleSize   the number of configurations to be generated
     * @param t            the t-wise coverage to aim for
     * @param strategy     the weight generation strategy
     * @param rounds       the number of rounds for sample generation, the weights are updated between rounds
     * @param extraSamples generate 10x configurations per round and select the best
     */
    public BaitalSampler(@NotNull IFeatureModel featureModel, int sampleSize, int t, int strategy,
                         int rounds, boolean extraSamples ) {
        super(featureModel);
        this.sampleSize = sampleSize;
        this.t = t;
        this.strategy = strategy;
        this.rounds = rounds;
        this.extraSamples = extraSamples;
    }

    @Override
    public @NotNull List<Map<String, Boolean>> sample(@Nullable List<AnalysisResult<?>> analysisResults,
                                                      @NotNull Path tmpPath)
            throws SamplerException, InterruptedException, IOException {
        LOGGER.info("Calculating weighted sample");
        CNF cnf = FeatureModelCNF.fromFeatureModel(this.featureModel);
        this.writeDimacsFile(tmpPath.resolve(BAITAL_INPUT_FILE), cnf);
        ProcessBuilder processBuilder = new ProcessBuilder(
                "python3", "baital.py", tmpPath.resolve(BAITAL_INPUT_FILE).toString(),
                "--preprocess-approximate", "--no-maxcov",
                "--outputdir", tmpPath.toString(),
                "--twise", Integer.toString(this.t),
                "--strategy", Integer.toString(this.strategy),
                "--rounds", Integer.toString(this.rounds),
                "--samples", Integer.toString(this.sampleSize));
        if (this.extraSamples) {
            processBuilder.command().add("--extra-samples");
        }
        processBuilder.directory(new File("/baital/src"));
        int exitCode = runSamplerProcess(processBuilder);
        if (exitCode != 0) {
            throw new SamplerException("baital exited with code " + exitCode);
        }
        Path baitalOutputFile = tmpPath.resolve(BAITAL_OUTPUT_FILE);
        List<Map<String, Boolean>> result = parseBaitalOutput(baitalOutputFile, cnf);
        LOGGER.info("Generated {} configurations", result.size());
        return result;
    }

    private @NotNull List<Map<String, Boolean>> parseBaitalOutput(Path baitalOutputFile, CNF cnf) throws IOException {
        try (Stream<String> lines  = Files.lines(baitalOutputFile)) {
            return lines.map(line -> { // Output Format: index, x y z
                String[] parts = line.split(",");
                if (parts.length < 2) {
                    throw new RuntimeException("Invalid output line: " + line);
                }
                String[] literals = parts[1].trim().split(" ");
                return literalsToConfiguration(literals, cnf);
            }).toList();
        }
    }
}
