package edu.kit.varijoern.samplers;

import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.FeatureModelCNF;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.io.dimacs.DimacsWriter;
import edu.kit.varijoern.analyzers.AnalysisResult;
import jodd.io.StreamGobbler;
import org.apache.commons.io.input.TeeInputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This sampler uses <a href="https://github.com/davidfa71/BDDSampler">BDDSampler</a> to choose a sample of
 * configurations uniformly at random using BDDs.
 */
public class BddSampler extends DimacsSampler {
    public static final String NAME = "bddsampler";
    private static final String BDD_DIR = "/samplers/bddsampler";
    private static final String VAR_FILE = "subject-noXOR.var";
    private static final String EXP_FILE = "subject-noXOR.exp";
    private static final String DDDMP_FILE = "subject-noXOR.dddmp";

    // BDDSampler parameters.
    private final int sampleSize;

    /**
     * Creates a new {@link BddSampler} which generates samples for the specified feature model (expressed as
     * {@link IFeatureModel}).
     *
     * @param featureModel the feature model (expressed as {@link IFeatureModel}).
     * @param sampleSize   the number of configurations to be generated.
     */
    public BddSampler(@NotNull IFeatureModel featureModel, int sampleSize) {
        super(featureModel);
        this.sampleSize = sampleSize;
    }

    @Override
    public @NotNull List<Map<String, Boolean>> sample(@Nullable List<AnalysisResult<?>> analysisResults,
                                                      @NotNull Path tmpPath)
            throws SamplerException, InterruptedException, IOException {
        LOGGER.info("Calculating uniform sample using BDDSampler.");

        // Transform feature model to CNF and then from CNF to the formats required for building the BDD.
        CNF cnf = FeatureModelCNF.fromFeatureModel(this.featureModel);
        this.writeVarFile(tmpPath.resolve(BddSampler.VAR_FILE), cnf);
        this.writeExpFile(tmpPath.resolve(BddSampler.EXP_FILE), cnf);

        // Build BDD.
        LOGGER.info("Building BDD...");
        ProcessBuilder bddCreatorPB = new ProcessBuilder("./create_dddmp.sh",
                tmpPath.resolve(BddSampler.VAR_FILE).toString(), tmpPath.resolve(BddSampler.EXP_FILE).toString());
        bddCreatorPB.directory(new File(BddSampler.BDD_DIR));
        this.runSamplerProcess(bddCreatorPB);
        Path dddmpFilePath = Paths.get(BddSampler.BDD_DIR, BddSampler.DDDMP_FILE);
        LOGGER.info("Finished building BDD.");

        // Configure ProcessBuilder.
        ProcessBuilder samplerPB = new ProcessBuilder("./BDDSampler", Integer.toString(this.sampleSize),
                dddmpFilePath.toString());
        samplerPB.directory(new File(BddSampler.BDD_DIR + "/bin"));

        // Execute BDDSampler.
        // BDDSampler prints its sample to the standard output.
        LOGGER.info("Execute BDDSampler.");
        Process bddSamplerProcess = samplerPB.start();
        int exitCode;
        BufferedReader reader;
        List<String> lines = new ArrayList<>();
        try {
            InputStream teeStream = new TeeInputStream(bddSamplerProcess.getInputStream(), STREAM_LOGGER, true);
            reader = new BufferedReader(new InputStreamReader(teeStream));
            StreamGobbler errorGobbler = new StreamGobbler(bddSamplerProcess.getErrorStream(), STREAM_LOGGER);
            errorGobbler.start();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            exitCode = bddSamplerProcess.waitFor();
        } catch (InterruptedException e) {
            bddSamplerProcess.destroy();
            throw e;
        }
        if (exitCode != 0) {
            throw new SamplerException(String.format("BDDSampler exited with code %d.", exitCode));
        }

        List<Map<String, Boolean>> result = this.parseBDDSamplerOutput(lines, cnf);
        LOGGER.info("Sampled {} configurations using BDDSampler.", result.size());
        return result;
    }

    /**
     * Parse BDDSampler output into a sample, i.e., a {@link List} of {@link Map}s mapping features to their
     * selection status.
     *
     * @param lines the lines output to standard output by BDDSampler.
     * @param cnf   the {@link CNF} of the feature model used to translate the feature literals returned by BDDSampler
     *              into their correct feature names.
     * @return the sample created by BDDSampler represented as a {@link List} of {@link Map}s mapping features to
     * their selection status.
     */
    private @NotNull List<Map<String, Boolean>> parseBDDSamplerOutput(@NotNull List<String> lines, @NotNull CNF cnf) {
        List<Map<String, Boolean>> result = new ArrayList<>();
        for (String line : lines) {
            if (line.matches("^([01] )+$")) {
                Map<String, Boolean> configuration = this.literalsToConfigurationNoIndexes(line.split(" "), cnf);
                this.verifyConfiguration(configuration);
                result.add(configuration);
            }
        }
        return result;
    }

    /**
     * Build a file specifying all variables (i.e., features) of the feature model. This file is required for building
     * the BDD.
     *
     * @param file the {@link Path} to the output file to which the variables (i.e., feature of the feature model)
     *             should be written.
     * @param cnf  the {@link CNF} representing the feature model.
     * @throws SamplerException if an I/O error occurs when trying to write to <code>file</code>.
     */
    private void writeVarFile(@NotNull Path file, @NotNull CNF cnf) throws SamplerException {
        int numberOfVariables = cnf.getVariables().size();
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= numberOfVariables; i++) {
            sb.append(i).append(" ");
        }

        try {
            Files.writeString(file, sb.toString());
        } catch (IOException e) {
            throw new SamplerException("Could not write var file.", e);
        }
    }

    /**
     * Build a file specifying all expressions/clauses (i.e., constraints) of the feature model. This file is required
     * for building the BDD.
     *
     * @param file the {@link Path} to the output file to which the expressions/clauses (i.e., constraints of the
     *             feature model) should be written.
     * @param cnf  the {@link CNF} representing the feature model.
     * @throws SamplerException if an I/O error occurs when trying to write to <code>file</code> or if invalid
     * expressions/clauses were extracted from <code>cnf</code>.
     */
    private void writeExpFile(@NotNull Path file, @NotNull CNF cnf) throws SamplerException {
        DimacsWriter dimacsWriter = new DimacsWriter(cnf);
        dimacsWriter.setWritingVariableDirectory(false);
        String[] dimacsLines = dimacsWriter.write().split("\n");

        List<String> expLines = new ArrayList<>();
        for (String line : dimacsLines) {
            if (!line.matches("^(-?\\d+ )+0$")) {
                continue;
            }
            if (line.length() < 3) {
                throw new SamplerException(String.format("Found invalid DIMACS line \"%s\" when extracting " +
                        "expressions/clauses from the feature model.", line));
            }

            String expLine = line.substring(0, line.length() - 2)
                    .replaceAll(" ", " or ")
                    .replaceAll("-", "not ");
            expLines.add(expLine);
        }

        try {
            Files.write(file, expLines);
        } catch (IOException e) {
            throw new SamplerException("Could not write exp file.", e);
        }
    }
}