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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * This sampler chooses a sample of configurations uniformly at random using BDDs.
 */
public class UniformBddSampler extends DimacsSampler {
    public static final String NAME = "bddsampler";

    private static final String BDD_DIR = "/BDDSampler";
    private static final String EXP_FILE = "subject-noXOR.exp";
    private static final String VAR_FILE = "subject-noXOR.var";
    private static final String DDDMP_FILE = "subject-noXOR.dddmp";

    private final int sampleSize;

    /**
     * Creates a new {@link UniformBddSampler} which generates samples for the specified feature model.
     *
     * @param featureModel the feature model
     * @param sampleSize   the number of configurations to be generated
     */
    public UniformBddSampler(@NotNull IFeatureModel featureModel, int sampleSize) {
        super(featureModel);
        this.sampleSize = sampleSize;
    }

    @Override
    public @NotNull List<Map<String, Boolean>> sample(@Nullable List<AnalysisResult<?>> analysisResults,
                                                      @NotNull Path tmpPath)
            throws SamplerException, InterruptedException, IOException {
        LOGGER.info("Calculating uniform sample using BDDSampler");

        CNF cnf = FeatureModelCNF.fromFeatureModel(this.featureModel);
        this.writeExpFile(tmpPath.resolve(EXP_FILE), cnf);
        this.writeVarFile(tmpPath.resolve(VAR_FILE), cnf);

        // Building BDD
        ProcessBuilder bddCreatorPB = new ProcessBuilder("./create_dddmp.sh",
                tmpPath.resolve(VAR_FILE).toString(), tmpPath.resolve(EXP_FILE).toString());
        bddCreatorPB.directory(new File(BDD_DIR));
        this.runSamplerProcess(bddCreatorPB);
        Path dddmpFilePath = Paths.get(BDD_DIR, DDDMP_FILE);

        // Executing Sampler
        ProcessBuilder samplerPB  = new ProcessBuilder("./BDDSampler", Integer.toString(this.sampleSize),
                dddmpFilePath.toString());
        samplerPB.directory(new File(BDD_DIR + "/bin"));

        String oldLdPath = samplerPB.environment().get("LD_LIBRARY_PATH");
        String newLdPath = BDD_DIR + "/lib";
        if (oldLdPath != null && !oldLdPath.isBlank() ) {
            newLdPath = newLdPath + ":" + oldLdPath;
        }
        samplerPB.environment().put("LD_LIBRARY_PATH", newLdPath);

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
            throw new SamplerException("BDDSampler exited with code " + exitCode);
        }
        List<Map<String, Boolean>> result = this.parseBDDSamplerOutput(lines, cnf);

        LOGGER.info("Generated {} configurations", result.size());
        return result;
    }

    private @NotNull List<Map<String, Boolean>> parseBDDSamplerOutput(List<String> lines, CNF cnf) {
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

    private void writeVarFile(Path file, CNF cnf) throws SamplerException {
        int numberVars = cnf.getVariables().size();
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= numberVars; i++) {
            sb.append(i).append(" ");
        }
        try {
            Files.writeString(file, sb.toString());
        } catch (IOException e) {
            throw new SamplerException("Could not write var file", e);
        }
    }

    private void writeExpFile(Path file, CNF cnf) throws SamplerException {
        DimacsWriter dimacsWriter = new DimacsWriter(cnf);
        dimacsWriter.setWritingVariableDirectory(false);
        String dimacsString = dimacsWriter.write();
        String[] dimacsLines = dimacsString.split("\n");
        List<String> expLines = new ArrayList<>();
        for (String line : dimacsLines) {
            if (!line.matches("^(-?\\d+ )+0$")) {
                continue;
            }
            if (line.length() < 3) {
                throw new SamplerException("Invalid dimacs line: " + line);
            }
            String expLine = line.substring(0, line.length() - 2).replaceAll(" ", " or ").replaceAll("-", "not ");
            expLines.add(expLine);
        }
        try {
            Files.write(Paths.get(file.toString()), expLines);
        } catch (IOException e) {
            throw new SamplerException("Could not write exp file", e);
        }
    }
}
