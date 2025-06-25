package edu.kit.varijoern.featuremodel.tortekmax;

import de.ovgu.featureide.fm.core.base.IConstraint;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.IFeatureModelElement;
import de.ovgu.featureide.fm.core.base.impl.Constraint;
import de.ovgu.featureide.fm.core.base.impl.Feature;
import de.ovgu.featureide.fm.core.io.manager.FeatureModelManager;
import de.ovgu.featureide.fm.core.io.xml.XmlFeatureModelFormat;
import edu.kit.varijoern.featuremodel.FeatureModelReader;
import edu.kit.varijoern.featuremodel.FeatureModelReaderException;
import edu.kit.varijoern.featuremodel.featureide.FeatureIDEFMReader;
import jodd.io.StreamGobbler;
import jodd.util.ResourcesUtil;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.io.IoBuilder;
import org.jetbrains.annotations.NotNull;
import org.prop4j.Literal;
import org.prop4j.Not;

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
 * <a href="https://github.com/paulgazz/kmax">kmax</a>.
 */
public class TorteKmaxFMReader implements FeatureModelReader {
    public static final String NAME = "torte-kmax";
    private static final Map<String, String> EXPERIMENT_SCRIPT_FILES = Map.of(
            "linux", "linux-working-tree-kmax.sh",
            "busybox", "busybox-working-tree-kmax.sh",
            "fiasco", "fiasco-working-tree-kmax.sh",
            "axtls", "axtls-working-tree-kmax.sh",
            "toybox", "toybox-working-tree-kmax.sh"
    );
    private static final Logger LOGGER = LogManager.getLogger();
    private static final OutputStream STREAM_LOGGER = IoBuilder.forLogger().setLevel(Level.INFO).buildOutputStream();

    private final @NotNull Path sourcePath;
    private final @NotNull String system;

    /**
     * Creates a new {@link TorteKmaxFMReader} that reads the feature model from the Kconfig files located in the
     * specified source directory. Because different code bases require different methods to extract the feature model,
     * the system parameter is used to determine which method to use.
     *
     * @param sourcePath the path to the source directory. Must be absolute.
     * @param system     the system to extract the feature model from
     */
    public TorteKmaxFMReader(@NotNull Path sourcePath, @NotNull String system) {
        this.sourcePath = sourcePath;
        if (!EXPERIMENT_SCRIPT_FILES.containsKey(system)) {
            throw new IllegalArgumentException("Unknown system: " + system);
        }
        this.system = system;
    }

    @Override
    public @NotNull IFeatureModel read(@NotNull Path tmpPath)
            throws IOException, FeatureModelReaderException, InterruptedException {
        LOGGER.info("Reading feature model from Kconfig files in {}", this.sourcePath);
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
                LOGGER.warn("Could not save feature model");
            }
            LOGGER.info("Feature model read successfully");
            return featureModel;
        } finally {
            // The source code probably takes a large amount of disk space.
            FileUtils.deleteDirectory(inputPath.toFile());
        }
    }

    private void runReader(@NotNull Path tmpPath)
            throws IOException, FeatureModelReaderException, InterruptedException {
        ProcessBuilder readerProcessBuilder = new ProcessBuilder("bash", getExperimentScriptName())
                .directory(tmpPath.toFile());
        readerProcessBuilder.environment().put("TORTE_INPUT_DIRECTORY", tmpPath.resolve("input").toString());
        readerProcessBuilder.environment().put("TORTE_OUTPUT_DIRECTORY", tmpPath.resolve("output").toString());
        Process readerProcess = readerProcessBuilder.start();
        StreamGobbler stdoutGobbler = new StreamGobbler(readerProcess.getInputStream(), STREAM_LOGGER);
        StreamGobbler stderrGobbler = new StreamGobbler(readerProcess.getErrorStream(), STREAM_LOGGER);
        stdoutGobbler.start();
        stderrGobbler.start();

        int readerExitCode;
        try {
            readerExitCode = readerProcess.waitFor();
            stdoutGobbler.join();
            stderrGobbler.join();
        } catch (InterruptedException e) {
            readerProcess.destroy();
            throw e;
        }
        if (readerExitCode != 0) {
            throw new FeatureModelReaderException("Torte exited with non-zero exit code: " + readerExitCode);
        }
    }

    private @NotNull Path findGeneratedFeatureModel(@NotNull Path tmpPath)
            throws IOException, FeatureModelReaderException {
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
     * @param tmpPath      the absolute path to the temporary directory of the feature model reader
     * @throws FeatureModelReaderException if the feature model could not be postprocessed
     */
    private void postprocessFeatureModel(@NotNull IFeatureModel featureModel, @NotNull Path tmpPath)
            throws FeatureModelReaderException {
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
                    LOGGER.debug("Adding probably unconstrained feature {}", featureName);
                    Feature feature = new Feature(featureModel, featureName);
                    featureModel.addFeature(feature);
                    featureModel.getStructure().getRoot().addChild(feature.getStructure());
                }
            }

            if (this.system.equals("busybox")) {
                // BusyBox fails to build when WERROR is enabled. Since WERROR does not modify the final product, it is
                // safe to disable it.
                featureModel.addConstraint(new Constraint(featureModel, new Not(new Literal("WERROR"))));
            } else if (this.system.equals("axtls")) {
                // Vari-Joern only supports Linux
                featureModel.addConstraint(new Constraint(featureModel, new Literal("CONFIG_PLATFORM_LINUX")));
            }

            deleteFeatures(featureModel, nonTristateFeatures);
        } catch (IOException e) {
            throw new FeatureModelReaderException("Could not add unconstrained features due to an I/O error", e);
        }
    }

    private static void deleteFeatures(@NotNull IFeatureModel featureModel, @NotNull List<String> nonTristateFeatures) {
        for (String feature : nonTristateFeatures) {
            LOGGER.debug("Feature {} does not appear to be tristate.", feature);
            featureModel.deleteFeature(featureModel.getFeature(feature));
        }
        List<IConstraint> constraints = featureModel.getConstraints();
        for (int i = constraints.size() - 1; i >= 0; i--) {
            IConstraint constraint = constraints.get(i);
            if (constraint.getContainedFeatures().stream()
                    .map(IFeatureModelElement::getName)
                    .anyMatch(nonTristateFeatures::contains)) {
                featureModel.removeConstraint(i);
                LOGGER.debug("Constraint {} contains non-tristate feature", constraint);
            }
        }
    }

    @NotNull
    private String getExperimentScriptName() {
        return EXPERIMENT_SCRIPT_FILES.get(this.system);
    }

    /**
     * Returns whether this reader supports the specified system.
     *
     * @param system the system to check
     * @return whether this reader supports the specified system
     */
    public static boolean supportsSystem(@NotNull String system) {
        return EXPERIMENT_SCRIPT_FILES.containsKey(system);
    }
}
