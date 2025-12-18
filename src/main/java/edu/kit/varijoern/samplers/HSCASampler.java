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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

/**
 * This sampler chooses a sample of configurations that achieves t-wise coverage.
 */
public class HSCASampler extends DimacsSampler {
    public static final String NAME = "hsca";

    private static final String HSCA_DIR = "/hsca";
    private static final String HSCA_INPUT_FILE_NAME = "model.cnf";
    private static final String HSCA_OUTPUT_FILE_NAME = "model.out";
    private static final String HSCA_MODEL_FILE_NAME = "model.model";
    private static final String HSCA_CONSTRAINTS_FILE_NAME = "model.constraints";

    private final int t;
    private final int cutoffTime;
    private final int l;

    /**
     * Creates a new {@link HSCASampler} which generates samples for the specified feature model.
     *
     * @param featureModel the feature model
     * @param t            the parameter t
     * @param cutoffTime   the cutoff time for the second optimization pass
     * @param l            the termination criterion for the first optimization pass
     */
    public HSCASampler(@NotNull IFeatureModel featureModel, int t, int cutoffTime, int l) {
        super(featureModel);
        this.t = t;
        this.cutoffTime = cutoffTime;
        this.l = l;
    }

    @Override
    public @NotNull List<Map<String, Boolean>> sample(@Nullable List<AnalysisResult<?>> analysisResults,
                                                      @NotNull Path tmpPath)
            throws SamplerException, InterruptedException, IOException {
        LOGGER.info("Calculating HSCA t-wise sample");
        CNF cnf = FeatureModelCNF.fromFeatureModel(this.featureModel);
        Path inputFile = tmpPath.resolve(HSCA_INPUT_FILE_NAME);
        this.writeDimacsFile(inputFile, cnf);

        // Convert dimacs CNF into files required by HSCA
        Path modelFile = tmpPath.resolve(HSCA_MODEL_FILE_NAME);
        Files.createFile(modelFile);
        Path constraintsFile = tmpPath.resolve(HSCA_CONSTRAINTS_FILE_NAME);
        Files.createFile(constraintsFile);
        ProcessBuilder converterPB = new ProcessBuilder("python3", "formatencoding.py",
                inputFile.toString(), Integer.toString(t), modelFile.toString(), constraintsFile.toString()
        );
        converterPB.directory(new File(HSCA_DIR));
        int converterExitCode = this.runSamplerProcess(converterPB);
        if (converterExitCode != 0) {
            throw new SamplerException("Model converter exited with code " + converterExitCode);
        }

        // Run HSCA
        Path outputFile = tmpPath.resolve(HSCA_OUTPUT_FILE_NAME);
        Random random = new Random();
        ProcessBuilder samplerPB = new ProcessBuilder("python3", "run_HSCA.py",
                modelFile.toString(), constraintsFile.toString(), outputFile.toString(),
                "-seed", Integer.toString(random.nextInt()),
                "-cutoff_time", Integer.toString(this.cutoffTime),
                "-L", Integer.toString(this.l)
        );
        samplerPB.directory(new File(HSCA_DIR));
        int samplerExitCode = this.runSamplerProcess(samplerPB);
        if (samplerExitCode != 0) {
            throw new SamplerException("HSCA exited with code " + samplerExitCode);
        }
        List<Map<String, Boolean>> result = parseHSCAOutput(outputFile, cnf);
        LOGGER.info("Generated {} configurations", result.size());
        return result;
    }

    private @NotNull List<Map<String, Boolean>> parseHSCAOutput(Path hscaOutputFile, CNF cnf) throws IOException {
        try (Stream<String> lines = Files.lines(hscaOutputFile)) {
            return lines.filter(s -> !s.isBlank()).map(line -> {
                Map<String, Boolean> configuration = new HashMap<>();
                String[] literals = line.split(" ");
                for (int i = 0; i < literals.length; i++) {
                    int literal = Integer.parseInt(literals[i]);
                    String feature = cnf.getVariables().getName(i + 1);
                    configuration.put(feature, literal % 2 != 0);
                }
                this.verifyConfiguration(configuration);
                return configuration;
            }).toList();
        }
    }
}
