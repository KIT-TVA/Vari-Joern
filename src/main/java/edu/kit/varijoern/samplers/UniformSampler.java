package edu.kit.varijoern.samplers;

import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.FeatureModelCNF;
import de.ovgu.featureide.fm.core.base.IConstraint;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.io.dimacs.DimacsWriter;
import edu.kit.varijoern.analyzers.AnalysisResult;
import jodd.io.StreamGobbler;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.io.IoBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * This sampler chooses a sample of configurations uniformly at random.
 */
public class UniformSampler implements Sampler {
    public static final String NAME = "uniform";

    private static final String SMARCH_OUTPUT_DIR = "smarch";
    private static final String SMARCH_INPUT_FILE = "model.dimacs";
    private static final String SMARCH_OUTPUT_FILE_PATTERN = "model_%d.samples";

    private static final Logger LOGGER = LogManager.getLogger();
    private static final OutputStream STREAM_LOGGER = IoBuilder.forLogger().setLevel(Level.DEBUG).buildOutputStream();

    private final IFeatureModel featureModel;
    private final int sampleSize;

    /**
     * Creates a new {@link UniformSampler} which generates samples for the specified feature model.
     *
     * @param featureModel the feature model
     * @param sampleSize   the number of configurations to be generated
     */
    public UniformSampler(@NotNull IFeatureModel featureModel, int sampleSize) {
        this.featureModel = featureModel;
        this.sampleSize = sampleSize;
    }

    @Override
    public @NotNull List<Map<String, Boolean>> sample(@Nullable List<AnalysisResult<?>> analysisResults,
                                                      @NotNull Path tmpPath)
            throws SamplerException, InterruptedException, IOException {
        LOGGER.info("Calculating uniform sample");
        CNF cnf = FeatureModelCNF.fromFeatureModel(this.featureModel);
        try {
            Files.writeString(tmpPath.resolve(SMARCH_INPUT_FILE), new DimacsWriter(cnf).write(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SamplerException("Could not write DIMACS file", e);
        }

        Path smarchOutputDir = tmpPath.resolve(SMARCH_OUTPUT_DIR);
        ProcessBuilder processBuilder = new ProcessBuilder("smarch_opt",
                "-o", smarchOutputDir.toString(),
                "-p", String.valueOf(Runtime.getRuntime().availableProcessors()),
                tmpPath.resolve("model.dimacs").toString(), Integer.toString(this.sampleSize));
        Process smarchProcess = processBuilder.start();
        int exitCode;
        try {
            StreamGobbler stdoutGobbler = new StreamGobbler(smarchProcess.getInputStream(), STREAM_LOGGER);
            StreamGobbler stderrGobbler = new StreamGobbler(smarchProcess.getErrorStream(), STREAM_LOGGER);
            stdoutGobbler.start();
            stderrGobbler.start();
            exitCode = smarchProcess.waitFor();
        } catch (InterruptedException e) {
            smarchProcess.destroy();
            throw e;
        }
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
            return lines
                    .map(line -> {
                        String[] literals = line.split(",");
                        Map<String, Boolean> configuration = new HashMap<>();
                        for (String literalString : literals) {
                            int literal = Integer.parseInt(literalString);
                            String feature = cnf.getVariables().getName(literal);
                            configuration.put(feature, literal > 0);
                        }

                        // Sanity check: verify that the configuration satisfies the constraints
                        for (IConstraint constraint : this.featureModel.getConstraints()) {
                            if (!constraint.getNode().getValue(Collections.unmodifiableMap(configuration))) {
                                throw new RuntimeException("Configuration does not satisfy constraint " + constraint);
                            }
                        }
                        return configuration;
                    })
                    .toList();
        }
    }
}
