package edu.kit.varijoern.composers.kbuild;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.composers.ComposerException;
import jodd.io.StreamGobbler;
import jodd.util.ResourcesUtil;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.io.IoBuilder;
import org.jetbrains.annotations.NotNull;
import org.prop4j.Node;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Stream;

/**
 * A class used to compute the presence conditions of source files using kmax.
 * There are a few cases in which no presence condition can be determined:
 * <ul>
 *     <li>The file is does not include kbuild information (e.g. header files and generated files).</li>
 *     <li>The condition found by kmax includes unknown options.</li>
 * </ul>
 */
public class FilePresenceConditionMapper {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final OutputStream STREAM_LOGGER = IoBuilder.forLogger().setLevel(Level.DEBUG).buildOutputStream();

    private final @NotNull Map<Path, Node> filePresenceConditions = new HashMap<>();

    /**
     * Creates a new {@link FilePresenceConditionMapper} and tries computes the presence conditions of the files in the
     * specified source directory.
     *
     * @param sourcePath     the path to the source directory. Must be an absolute path.
     * @param system         the name of the system. Only busybox is supported at the moment. For any other system, no
     *                       presence conditions will be determined.
     * @param composerTmpDir the temporary directory of the composer. Must be an absolute path.
     * @throws IOException       if an I/O error occurs
     * @throws ComposerException if kmax fails or the presence conditions cannot be parsed
     */
    public FilePresenceConditionMapper(@NotNull Path sourcePath, @NotNull String system, @NotNull Path composerTmpDir,
                                       @NotNull IFeatureModel featureModel)
            throws IOException, ComposerException, InterruptedException {
        this.createKmaxallScript(composerTmpDir);
        if (system.equals("busybox")) {
            processKmaxOutput(runKmax(sourcePath, composerTmpDir), sourcePath, featureModel);
        }
    }

    /**
     * Creates a new {@link FilePresenceConditionMapper} with an empty presence condition map.
     */
    public FilePresenceConditionMapper() {
    }

    private void createKmaxallScript(@NotNull Path tmpDir) throws IOException {
        if (tmpDir.resolve("run-kmaxall.py").toFile().exists()) return;
        String script = ResourcesUtil.getResourceAsString("kmax/run-kmaxall.py");
        Path scriptPath = tmpDir.resolve("run-kmaxall.py");
        Files.writeString(scriptPath, script, StandardCharsets.UTF_8);
    }

    private static @NotNull String runKmax(@NotNull Path sourcePath, @NotNull Path composerTmpDir)
            throws IOException, ComposerException, InterruptedException {
        List<String> kmaxallArgs = new ArrayList<>(List.of(
                "python3",
                composerTmpDir.resolve("run-kmaxall.py").toString()
        ));
        try (Stream<Path> files = Files.walk(sourcePath)) {
            kmaxallArgs.addAll(files
                    .filter(path -> path.getFileName().toString().endsWith("Kbuild"))
                    .map(Path::toString)
                    .toList());
        }
        ProcessBuilder processBuilder = new ProcessBuilder(kmaxallArgs)
                .directory(sourcePath.toFile());
        String output;
        Process process = processBuilder.start();
        int exitCode;
        try {
            StreamGobbler stderrGobbler = new StreamGobbler(process.getErrorStream(), STREAM_LOGGER);
            stderrGobbler.start();
            output = IOUtils.toString(process.getInputStream(), Charset.defaultCharset());
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            process.destroy();
            throw e;
        }
        if (exitCode != 0) {
            throw new ComposerException("run-kmax.py exited with code " + exitCode);
        }
        return output;
    }

    private void processKmaxOutput(@NotNull String output, @NotNull Path sourcePath,
                                   @NotNull IFeatureModel featureModel)
            throws JsonProcessingException, ComposerException {
        TypeReference<HashMap<String, String>> typeRef = new TypeReference<>() {
        };
        Map<String, String> rawPresenceConditions = new ObjectMapper().readValue(output, typeRef);
        SMTLibConverter smtLibConverter = new SMTLibConverter();
        for (Map.Entry<String, String> entry : rawPresenceConditions.entrySet()) {
            Path path = Path.of(entry.getKey());
            if (path.isAbsolute()) {
                path = sourcePath.relativize(path);
            }
            try {
                Node presenceCondition = smtLibConverter.convert(entry.getValue());
                presenceCondition.modifyFeatureNames(name -> {
                    if (!name.startsWith("CONFIG_")) {
                        LOGGER.warn("Option in presence condition does not start with CONFIG_: {}; file: {}",
                                name, entry.getKey());
                        return name;
                    }
                    return name.substring("CONFIG_".length());
                });
                List<String> unknownFeatures = presenceCondition.getContainedFeatures().stream()
                        .filter(feature -> featureModel.getFeature(feature) == null)
                        .toList();
                if (!unknownFeatures.isEmpty()) {
                    StringBuilder warning = new StringBuilder();
                    warning.append("Presence condition contains unknown features: ")
                            .append(String.join(", ", unknownFeatures))
                            .append("; file: ")
                            .append(entry.getKey())
                            .append(System.lineSeparator())
                            .append("Changed from %s".formatted(presenceCondition));
                    presenceCondition = Node.replaceLiterals(presenceCondition, unknownFeatures, true);
                    warning.append(" to %s".formatted(presenceCondition));
                    LOGGER.warn(warning);
                }
                filePresenceConditions.put(path.normalize(), presenceCondition);
            } catch (ParseException e) {
                throw new ComposerException(
                        "Could not parse presence condition (%s at %d). Text was:%n%s"
                                .formatted(entry.getKey(), e.getErrorOffset(), entry.getValue()),
                        e
                );
            }
        }
    }

    /**
     * Returns the presence condition of the specified file if it could be determined.
     *
     * @param path the path to the (compiled) object file, relative to the source directory
     * @return the presence condition of the file or an empty optional if the presence condition could not be determined
     */
    public @NotNull Optional<Node> getPresenceCondition(@NotNull Path path) {
        return Optional.ofNullable(this.filePresenceConditions.get(path.normalize()));
    }
}
