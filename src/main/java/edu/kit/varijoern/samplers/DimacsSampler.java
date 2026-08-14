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

// TODO Consider moving the parseXYZOutput method of the inheritors to this class.

/**
 * Abstract class for samplers using the DIMACS format as input.
 */
public abstract class DimacsSampler implements Sampler {
    protected static final OutputStream STREAM_LOGGER = IoBuilder.forLogger().setLevel(Level.DEBUG).buildOutputStream();
    protected static final Logger LOGGER = LogManager.getLogger();

    protected final IFeatureModel featureModel;

    /**
     * Creates a new {@link DimacsSampler} which generates samples using the dimacs format as input.
     *
     * @param featureModel the feature model.
     */
    protected DimacsSampler(@NotNull IFeatureModel featureModel) {
        this.featureModel = featureModel;
    }

    /**
     * Runs processes used for the sampler.
     * TODO Consider pushing the method up to the Sampler interface or a util class.
     *
     * @param processBuilder the {@link ProcessBuilder} of the process to run.
     * @return the exit code of the process.
     * @throws IOException          if an I/O error occurs in the process.
     * @throws InterruptedException if the process is interrupted.
     */
    protected int runSamplerProcess(@NotNull ProcessBuilder processBuilder) throws IOException, InterruptedException {
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
     * Converts an array of literals (i.e., feature indices with positive sign for selected / negative sign for
     * unselected) to a configuration and verifies it against the feature model.
     * <p>
     * <strong>Note</strong>: The literals have to be indexes for the name of the feature and {@code "positive"} for true /
     * {@code "negative"} for false.
     *
     * @param literals an array of literals expressed as an array of {@link String}s (e.g., for the deselection of
     *                 feature 5, the entry would be -5).
     * @param cnf      the {@link CNF} of the feature model.
     * @return a {@link Map} describing the configuration.
     */
    protected Map<String, Boolean> literalsToConfiguration(@NotNull String[] literals, @NotNull CNF cnf) {
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
     * Converts an array of literals (i.e., feature indices with positive sign for selected / negative sign for
     * unselected) to a configuration and verifies it against the feature model.
     * Contrary to {@link DimacsSampler#literalsToConfiguration(String[], CNF)}, it assumes that the index of the
     * literals in the array represents their feature index.
     *
     * @param literals an array of literals expressed as an array of {@link String}s (e.g., for the deselection of
     *                 feature 5, the entry at index 4 would be a negative number).
     * @param cnf      the {@link CNF} of the feature model.
     * @return a {@link Map} describing the configuration.
     */
    protected Map<String, Boolean> literalsToConfigurationNoIndexes(@NotNull String[] literals, @NotNull CNF cnf) {
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
     * @param configuration the configuration to verify (expressed as a {@link Map} mapping feature names to their
     *                      boolean selection status).
     */
    protected void verifyConfiguration(@NotNull Map<String, Boolean> configuration) {
        for (IConstraint constraint : this.featureModel.getConstraints()) {
            if (!constraint.getNode().getValue(Collections.unmodifiableMap(configuration))) {
                throw new RuntimeException("Configuration does not satisfy constraint " + constraint);
            }
        }
    }

    /**
     * Write a {@link CNF} to a file in dimacs format.
     *
     * @param dimacsOutput the path of the dimacs file to write to.
     * @param cnf          the {@link CNF} of the feature model.
     * @throws SamplerException if I/O error occurs while writing the file.
     */
    protected void writeDimacsFile(@NotNull Path dimacsOutput, @NotNull CNF cnf) throws SamplerException {
        try {
            Files.writeString(dimacsOutput, new DimacsWriter(cnf).write(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SamplerException("Could not write dimacs file to " + dimacsOutput, e);
        }
    }
}
