package edu.kit.varijoern.featuremodel;

import de.ovgu.featureide.fm.core.base.IConstraint;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.IFeatureModelElement;
import de.ovgu.featureide.fm.core.base.impl.Feature;
import de.ovgu.featureide.fm.core.io.manager.FeatureModelManager;
import de.ovgu.featureide.fm.core.io.xml.XmlFeatureModelFormat;
import jodd.io.StreamGobbler;
import jodd.util.ResourcesUtil;
import org.apache.commons.io.FileUtils;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Reads a feature model from Kconfig files using <a href="https://github.com/ekuiter/torte/tree/main">torte</a> and
 * <a href="https://github.com/paulgazz/kmax">kmax</a>. So far, only the Linux kernel is supported.
 */
public class TorteKmaxFMReader implements FeatureModelReader {
    public static final String NAME = "torte-kmax";
    private static final Map<String, String> experimentScriptFiles = Map.of(
            "linux", "linux-working-tree-kmax.sh",
            "busybox", "busybox-working-tree-kmax.sh"
    );
    private static final Logger logger = LogManager.getLogger();
    private static final OutputStream streamLogger = IoBuilder.forLogger().setLevel(Level.INFO).buildOutputStream();

    private final Path sourcePath;
    private final String system;

    /**
     * Creates a new {@link TorteKmaxFMReader} that reads the feature model from the Kconfig files located in the
     * specified source directory. Because different code bases require different methods to extract the feature model,
     * the system parameter is used to determine which method to use.
     *
     * @param sourcePath the path to the source directory
     * @param system     the system to extract the feature model from
     */
    public TorteKmaxFMReader(Path sourcePath, String system) {
        this.sourcePath = sourcePath;
        if (!experimentScriptFiles.containsKey(system)) {
            throw new IllegalArgumentException("Unknown system: " + system);
        }
        this.system = system;
    }

    @Override
    public IFeatureModel read(Path tmpPath) throws IOException, FeatureModelReaderException {
        logger.info("Reading feature model from Kconfig files in {}", this.sourcePath);
        String readerScript = ResourcesUtil.getResourceAsString("torte/" + getExperimentScriptName());
        Path readerScriptPath = tmpPath.resolve(getExperimentScriptName());
        Files.writeString(readerScriptPath, readerScript, StandardCharsets.UTF_8);

        Path inputPath = tmpPath.resolve("input");
        Path sourcePath = inputPath.resolve(this.system);
        try {
            Files.createDirectories(inputPath);
            FileUtils.copyDirectory(this.sourcePath.toFile(), sourcePath.toFile());

            runReader(tmpPath);

            Path fmPath = findGeneratedFeatureModel(tmpPath);
            IFeatureModel featureModel = new FeatureIDEFMReader(fmPath).read(tmpPath);
            this.postprocessFeatureModel(featureModel, tmpPath);
            if (!FeatureModelManager.save(featureModel,
                    tmpPath.resolve("filtered-model.xml"),
                    new XmlFeatureModelFormat())) {
                logger.warn("Could not save feature model");
            }
            logger.info("Feature model read successfully");
            return featureModel;
        } finally {
            // The source code probably takes a large amount of disk space.
            FileUtils.deleteDirectory(inputPath.toFile());
        }
    }

    private void runReader(Path tmpPath) throws IOException, FeatureModelReaderException {
        ProcessBuilder readerProcessBuilder = new ProcessBuilder("bash", getExperimentScriptName())
                .directory(tmpPath.toFile());
        readerProcessBuilder.environment().put("TORTE_INPUT_DIRECTORY", tmpPath.resolve("input").toString());
        readerProcessBuilder.environment().put("TORTE_OUTPUT_DIRECTORY", tmpPath.resolve("output").toString());
        Process readerProcess = readerProcessBuilder.start();
        StreamGobbler stdoutGobbler = new StreamGobbler(readerProcess.getInputStream(), streamLogger);
        StreamGobbler stderrGobbler = new StreamGobbler(readerProcess.getErrorStream(), streamLogger);
        stdoutGobbler.start();
        stderrGobbler.start();

        int readerExitCode;
        try {
            readerExitCode = readerProcess.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException("Unexpected interruption of Torte process", e);
        }
        if (readerExitCode != 0) {
            throw new FeatureModelReaderException("Torte exited with non-zero exit code: " + readerExitCode);
        }
    }

    private Path findGeneratedFeatureModel(Path tmpPath) throws IOException, FeatureModelReaderException {
        try (Stream<Path> files = Files.walk(tmpPath.resolve("output/model_to_xml_featureide/" + this.system))) {
            List<Path> featureModelPaths = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".xml"))
                    .toList();
            if (featureModelPaths.isEmpty())
                throw new FeatureModelReaderException("No feature model found");
            if (featureModelPaths.size() > 1)
                throw new FeatureModelReaderException("Multiple candidates for feature model found");
            return featureModelPaths.get(0);
        }
    }

    /**
     * Performs a few steps to make the feature model more accurate:
     * 1. Adds features that are probably unconstrained
     * 2. Removes features that are not tristate or boolean
     *
     * @param featureModel the feature model to postprocess
     * @param tmpPath      the temporary path of the feature model reader
     * @throws FeatureModelReaderException if the feature model could not be postprocessed
     */
    private void postprocessFeatureModel(IFeatureModel featureModel, Path tmpPath) throws FeatureModelReaderException {
        try (Stream<Path> files = Files.walk(tmpPath.resolve("output/kconfig/" + this.system))) {
            List<Path> kextractorFiles = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".kextractor"))
                    .toList();
            if (kextractorFiles.isEmpty())
                throw new FeatureModelReaderException("No kextractor file found");
            if (kextractorFiles.size() > 1)
                throw new FeatureModelReaderException("Multiple possible kextractor files found");
            Path kextractorFile = kextractorFiles.get(0);
            List<String> kextractorLines = Files.readAllLines(kextractorFile);
            List<String> nonTristateFeatures = new ArrayList<>();
            for (String line : kextractorLines) {
                String[] parts = line.split(" ");
                if (parts.length == 0 || !parts[0].equals("config")) continue;
                if (parts.length != 3)
                    throw new FeatureModelReaderException("Unexpected number of parts in kextractor line: " + line);
                String featureName = parts[1].substring("CONFIG_".length());
                if (featureModel.getFeature(featureName) != null) {
                    if (!parts[2].equals("tristate") && !parts[2].equals("bool")) {
                        nonTristateFeatures.add(featureName);
                    }
                } else {
                    if (!parts[2].equals("tristate") && !parts[2].equals("bool")) continue;
                    if (!parts[1].startsWith("CONFIG_"))
                        throw new FeatureModelReaderException("Unexpected feature name in kextractor line: " + line);
                    logger.debug("Adding probably unconstrained feature {}", featureName);
                    Feature feature = new Feature(featureModel, featureName);
                    featureModel.addFeature(feature);
                    featureModel.getStructure().getRoot().addChild(feature.getStructure());
                }
            }
            deleteFeatures(featureModel, nonTristateFeatures);
        } catch (IOException e) {
            throw new FeatureModelReaderException("Could not add unconstrained features due to an I/O error", e);
        }
    }

    private static void deleteFeatures(IFeatureModel featureModel, List<String> nonTristateFeatures) {
        for (String feature : nonTristateFeatures) {
            logger.debug("Feature {} does not appear to be tristate.", feature);
            featureModel.deleteFeature(featureModel.getFeature(feature));
        }
        List<IConstraint> constraints = featureModel.getConstraints();
        for (int i = constraints.size() - 1; i >= 0; i--) {
            IConstraint constraint = constraints.get(i);
            if (constraint.getContainedFeatures().stream().map(IFeatureModelElement::getName).anyMatch(nonTristateFeatures::contains)) {
                featureModel.removeConstraint(i);
                logger.debug("Constraint {} contains non-tristate feature", constraint);
            }
        }
    }

    @NotNull
    private String getExperimentScriptName() {
        return experimentScriptFiles.get(this.system);
    }

    /**
     * Returns whether this reader supports the specified system.
     *
     * @param system the system to check
     * @return whether this reader supports the specified system
     */
    public static boolean supportsSystem(String system) {
        return experimentScriptFiles.containsKey(system);
    }
}
