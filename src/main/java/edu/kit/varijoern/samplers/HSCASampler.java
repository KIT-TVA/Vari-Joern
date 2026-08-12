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
 * This sampler uses <a href="https://github.com/chuanluocs/HSCA">HSCA</a> to choose a sample of configurations that
 * achieves t-wise feature interaction coverage.
 */
public class HSCASampler extends DimacsSampler {
    public static final String NAME = "hsca";
    private static final String HSCA_DIR = "/samplers/hsca";
    private static final String HSCA_INPUT_FILE_NAME = "model.cnf";
    private static final String HSCA_MODEL_FILE_NAME = "model.model";
    private static final String HSCA_CONSTRAINTS_FILE_NAME = "model.constraints";
    private static final String HSCA_OUTPUT_FILE_NAME = "model.out";

    // HSCA parameters.
    private final int t;
    private final int l;
    private final int cutoffTime;
    private final boolean useSecondOptimization;

    /**
     * Creates a new {@link HSCASampler} which generates samples for the specified feature model (expressed as
     * {@link IFeatureModel}).
     *
     * @param featureModel          the feature model (expressed as {@link IFeatureModel}).
     * @param t                     the t-wise feature interaction coverage that should be achieved.
     * @param l                     the termination criterion for the first optimization pass
     * @param cutoffTime            the cutoff time for the second optimization pass.
     * @param useSecondOptimization whether the second optimization pass should be enabled.
     */
    public HSCASampler(@NotNull IFeatureModel featureModel, int t, int l, int cutoffTime,
                       boolean useSecondOptimization) {
        super(featureModel);
        this.t = t;
        this.l = l;
        this.cutoffTime = cutoffTime;
        this.useSecondOptimization = useSecondOptimization;
    }

    @Override
    public @NotNull List<Map<String, Boolean>> sample(@Nullable List<AnalysisResult<?>> analysisResults,
                                                      @NotNull Path tmpPath)
            throws SamplerException, InterruptedException, IOException {
        LOGGER.info("Calculating t-wise sample using HSCA.");

        // Transform feature model to CNF and then from CNF to DIMACS.
        CNF cnf = FeatureModelCNF.fromFeatureModel(this.featureModel);
        Path inputFile = tmpPath.resolve(HSCASampler.HSCA_INPUT_FILE_NAME);
        this.writeDimacsFile(inputFile, cnf);

        // Convert DIMACS into files required by HSCA.
        Path modelFile = tmpPath.resolve(HSCASampler.HSCA_MODEL_FILE_NAME);
        Files.createFile(modelFile);
        Path constraintsFile = tmpPath.resolve(HSCASampler.HSCA_CONSTRAINTS_FILE_NAME);
        Files.createFile(constraintsFile);
        ProcessBuilder converterPB = new ProcessBuilder(
                "python3", "formatencoding.py",
                inputFile.toString(), // DIMACS.
                Integer.toString(t), // t.
                modelFile.toString(), // Output model file.
                constraintsFile.toString() // Output constraints file.
        );
        converterPB.directory(new File(HSCASampler.HSCA_DIR));
        int converterExitCode = this.runSamplerProcess(converterPB);
        if (converterExitCode != 0) {
            throw new SamplerException(String.format("Model converter exited with code %d.", converterExitCode));
        }

        // Configure ProcessBuilder.
        Path outputFile = tmpPath.resolve(HSCASampler.HSCA_OUTPUT_FILE_NAME);
        ProcessBuilder samplerPB = new ProcessBuilder("python3", "run_HSCA.py",
                modelFile.toString(), constraintsFile.toString(), outputFile.toString(),
                "-seed", Integer.toString(new Random().nextInt()),
                "-cutoff_time", Integer.toString(this.cutoffTime),
                "-L", Integer.toString(this.l),
                "-use_second_optimization", this.useSecondOptimization ? "1" : "0"
        );
        samplerPB.directory(new File(HSCA_DIR));

        // Execute HSCA.
        int samplerExitCode = this.runSamplerProcess(samplerPB);
        if (samplerExitCode != 0) {
            throw new SamplerException(String.format("HSCA exited with code %d.", samplerExitCode));
        }
        List<Map<String, Boolean>> result = parseHSCAOutput(outputFile, cnf);
        LOGGER.info("Sampled {} configurations using HSCA.", result.size());
        return result;
    }

    /**
     * Parse HSCA output into a sample, i.e., a {@link List} of {@link Map}s mapping features to their
     * selection status.
     *
     * @param hscaOutputFile the {@link Path} at which the output by HSCA can be found.
     * @param cnf            the {@link CNF} of the feature model used to translate the feature literals returned by
     *                       HSCA into their correct feature names.
     * @return the sample created by HSCA represented as a {@link List} of {@link Map}s mapping features to
     * their selection status.
     * @throws IOException if an I/O error occurs opening the file.
     */
    private @NotNull List<Map<String, Boolean>> parseHSCAOutput(@NotNull Path hscaOutputFile,
                                                                @NotNull CNF cnf) throws IOException {
        try (Stream<String> lines = Files.lines(hscaOutputFile)) {
            return lines.filter(s -> !s.isBlank()).map(line -> {
                Map<String, Boolean> configuration = new HashMap<>();
                String[] literals = line.split(" ");

                for (int i = 0; i < literals.length; i++) {
                    int literal = Integer.parseInt(literals[i]);
                    String featureName = cnf.getVariables().getName(i + 1);
                    configuration.put(featureName, literal % 2 != 0);
                }

                this.verifyConfiguration(configuration);
                return configuration;
            }).toList();
        }
    }
}
