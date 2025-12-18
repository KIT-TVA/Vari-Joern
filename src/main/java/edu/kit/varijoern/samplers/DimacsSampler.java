package edu.kit.varijoern.samplers;

import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.base.IConstraint;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.io.dimacs.DimacsWriter;
import jodd.io.StreamGobbler;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.io.IoBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Samplers using the dimacs format as the input.
 */
public abstract class DimacsSampler implements Sampler {
    protected static final OutputStream STREAM_LOGGER = IoBuilder.forLogger().setLevel(Level.DEBUG).buildOutputStream();
    protected static final Logger LOGGER = LogManager.getLogger();

    protected final IFeatureModel featureModel;

    /**
     * Creates a new {@link DimacsSampler} which generates samples, using the dimacs format as input.
     *
     * @param featureModel the feature model
     */
    protected DimacsSampler(@NotNull IFeatureModel featureModel) {
        this.featureModel = featureModel;
    }

    /**
     * Runs processes used for the sampler.
     *
     * @param processBuilder the ProcessBuilder of the process to run
     * @return the exit code of the process
     * @throws IOException          if an I/O error occurs in the process
     * @throws InterruptedException if the process is interrupted
     */
    protected int runSamplerProcess(ProcessBuilder processBuilder) throws IOException, InterruptedException {
        Process process = processBuilder.start();
        int exitCode;
        try {
            StreamGobbler stdoutGobbler = new StreamGobbler(process.getInputStream(), STREAM_LOGGER);
            StreamGobbler stderrGobbler = new StreamGobbler(process.getErrorStream(), STREAM_LOGGER);
            stdoutGobbler.start();
            stderrGobbler.start();
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            process.destroy();
            throw e;
        }
        return exitCode;
    }

    /**
     * Converts an array of literals to a configuration and verifies it against the feature model.
     * The literals have to be indexes for the name of the feature and positive for true / negative for false.
     *
     * @param literals an array of literals
     * @param cnf      the cnf of the feature model
     * @return a map describing the configuration
     */
    protected Map<String, Boolean> literalsToConfiguration(String[] literals, CNF cnf) {
        Map<String, Boolean> configuration = new HashMap<>();
        for (String literalString : literals) {
            int literal = Integer.parseInt(literalString);
            String feature = cnf.getVariables().getName(literal);
            configuration.put(feature, literal > 0);
        }

        // Sanity check: verify that the configuration satisfies the constraints
        this.verifyConfiguration(configuration);
        return configuration;
    }

    /**
     * Converts an array of literals to a configuration and verifies it against the feature model.
     * The literals have to be in the same order as the variables in the CNF and positive for true / negative for false.
     *
     * @param literals an array of literals
     * @param cnf      the cnf of the feature model
     * @return a map describing the configuration
     */
    protected Map<String, Boolean> literalsToConfigurationNoIndexes(String[] literals, CNF cnf) {
        Map<String, Boolean> configuration = new HashMap<>();
        for (int i = 0; i < literals.length; i++) {
            int literal = Integer.parseInt(literals[i]);
            String feature = cnf.getVariables().getName(i + 1);
            configuration.put(feature, literal > 0);
        }
        // Sanity check: verify that the configuration satisfies the constraints
        this.verifyConfiguration(configuration);
        return configuration;
    }

    /**
     * Verifies a configuration against the feature model.
     *
     * @param configuration the configuration to verify
     */
    protected void verifyConfiguration(Map<String, Boolean> configuration) {
        for (IConstraint constraint : this.featureModel.getConstraints()) {
            if (!constraint.getNode().getValue(Collections.unmodifiableMap(configuration))) {
                throw new RuntimeException("Configuration does not satisfy constraint " + constraint);
            }
        }
    }

    /**
     * Writes the CNF to a file in dimacs format.
     *
     * @param inputFile the file to write to.
     * @param cnf       the cnf of the feature model
     * @throws SamplerException if I/O error occurs while writing the file.
     */
    protected void writeDimacsFile(Path inputFile, CNF cnf) throws SamplerException {
        try {
            Files.writeString(inputFile, new DimacsWriter(cnf).write(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SamplerException("Could not write dimacs file", e);
        }
    }
}
