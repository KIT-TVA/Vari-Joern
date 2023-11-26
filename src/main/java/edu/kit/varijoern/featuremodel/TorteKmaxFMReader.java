package edu.kit.varijoern.featuremodel;

import de.ovgu.featureide.fm.core.base.IConstraint;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.IFeatureModelElement;
import de.ovgu.featureide.fm.core.base.impl.Feature;
import de.ovgu.featureide.fm.core.io.manager.FeatureModelManager;
import de.ovgu.featureide.fm.core.io.xml.XmlFeatureModelFormat;
import jodd.util.ResourcesUtil;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
    private static final Pattern nonTristateFeaturePattern = Pattern.compile("CONFIG_(\\w+)=[^ymn].*");

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
            this.addUnconstrainedFeatures(featureModel, tmpPath);
            this.removeNonTristateFeatures(featureModel, sourcePath);
            if (!FeatureModelManager.save(featureModel,
                tmpPath.resolve("filtered-model.xml"),
                new XmlFeatureModelFormat())) {
                System.err.println("Could not save feature model");
            }
            return featureModel;
        } finally {
            // The source code probably takes a large amount of disk space.
            FileUtils.deleteDirectory(inputPath.toFile());
        }
    }

    private void runReader(Path tmpPath) throws IOException, FeatureModelReaderException {
        ProcessBuilder readerProcessBuilder = new ProcessBuilder("bash", getExperimentScriptName())
            .inheritIO()
            .directory(tmpPath.toFile());
        readerProcessBuilder.environment().put("TORTE_INPUT_DIRECTORY", tmpPath.resolve("input").toString());
        readerProcessBuilder.environment().put("TORTE_OUTPUT_DIRECTORY", tmpPath.resolve("output").toString());
        Process readerProcess = readerProcessBuilder.start();
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

    // This is a workaround: Torte should do this for us, but it does not.
    private void addUnconstrainedFeatures(IFeatureModel featureModel, Path tmpPath) throws FeatureModelReaderException {
        try (Stream<Path> files = Files.walk(tmpPath.resolve("output/kconfig/" + this.system))) {
            List<Path> featureListPaths = files.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".features"))
                .toList();
            if (featureListPaths.isEmpty())
                throw new FeatureModelReaderException("No feature list found");
            if (featureListPaths.size() > 1)
                throw new FeatureModelReaderException("Multiple candidates for feature list found");
            Path featureListPath = featureListPaths.get(0);
            List<String> featureList = Files.readAllLines(featureListPath);
            for (String feature : featureList) {
                if (feature.chars().anyMatch(codePoint ->
                    (!Character.isAlphabetic(codePoint) || !Character.isUpperCase(codePoint))
                        && codePoint != '_'))
                    continue;
                if (featureModel.getFeature(feature) == null) {
                    System.out.printf("Feature %s is probably unconstrained and was added to the feature model.%n",
                        feature);
                    featureModel.addFeature(new Feature(featureModel, feature));
                }
            }
        } catch (IOException e) {
            throw new FeatureModelReaderException("Could not add unconstrained features due to an I/O error", e);
        }
    }

    private void removeNonTristateFeatures(IFeatureModel featureModel, Path tmpSourcePath)
        throws IOException, FeatureModelReaderException {
        // Run make defconfig
        ProcessBuilder makeDefconfigProcessBuilder = new ProcessBuilder("make", "defconfig")
            .inheritIO()
            .directory(tmpSourcePath.toFile());
        Process makeDefconfigProcess = makeDefconfigProcessBuilder.start();
        int makeDefconfigExitCode;
        try {
            makeDefconfigExitCode = makeDefconfigProcess.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException("Unexpected interruption of make process", e);
        }
        if (makeDefconfigExitCode != 0) {
            throw new FeatureModelReaderException("make defconfig exited with non-zero exit code: " + makeDefconfigExitCode);
        }

        // Read .config file
        Path configPath = tmpSourcePath.resolve(".config");
        List<String> configLines = Files.readAllLines(configPath);
        Set<String> nonTristateFeatures = configLines.stream()
            .map(nonTristateFeaturePattern::matcher)
            .filter(Matcher::matches)
            .map(matcher -> matcher.group(1))
            .collect(Collectors.toSet());
        for (String feature : nonTristateFeatures) {
            System.err.printf("Feature %s does not appear to be tristate.%n", feature);
            featureModel.deleteFeature(featureModel.getFeature(feature));
        }
        List<IConstraint> constraints = featureModel.getConstraints();
        for (int i = constraints.size() - 1; i >= 0; i--) {
            IConstraint constraint = constraints.get(i);
            if (constraint.getContainedFeatures().stream().map(IFeatureModelElement::getName).anyMatch(nonTristateFeatures::contains)) {
                featureModel.removeConstraint(i);
                System.err.printf("Constraint %s contains non-tristate feature%n", constraint);
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
