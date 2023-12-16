package edu.kit.varijoern.composers.kbuild;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.kit.varijoern.composers.ComposerException;
import jodd.util.ResourcesUtil;
import org.apache.commons.io.IOUtils;
import org.prop4j.Node;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A class used to create {@link KbuildFeatureMapper}s. Computes the presence conditions of source files using kmax.
 * There are a few cases in which no presence condition can be determined:
 * <ul>
 *     <li>The file is does not include kbuild information (e.g. header files and generated files).</li>
 *     <li>The condition found by kmax includes unknown options.</li>
 * </ul>
 */
public class KbuildFeatureMapperCreator {
    private final Map<Path, Node> filePresenceConditions = new HashMap<>();

    /**
     * Creates a new {@link KbuildFeatureMapperCreator} and tries computes the presence conditions of the files in the
     * specified source directory.
     *
     * @param sourcePath     the path to the source directory
     * @param system         the name of the system. Only busybox is supported at the moment. For any other system, no presence
     *                       conditions will be determined.
     * @param composerTmpDir the temporary directory of the composer
     * @throws IOException       if an I/O error occurs
     * @throws ComposerException if kmax fails or the presence conditions cannot be parsed
     */
    public KbuildFeatureMapperCreator(Path sourcePath, String system, Path composerTmpDir) throws IOException, ComposerException {
        this.createKmaxallScript(composerTmpDir);
        if (system.equals("busybox")) {
            processKmaxOutput(runKmax(sourcePath, composerTmpDir), sourcePath);
        }
    }

    private void createKmaxallScript(Path tmpDir) throws IOException {
        if (tmpDir.resolve("run-kmaxall.py").toFile().exists()) return;
        String script = ResourcesUtil.getResourceAsString("kmax/run-kmaxall.py");
        Path scriptPath = tmpDir.resolve("run-kmaxall.py");
        Files.writeString(scriptPath, script, StandardCharsets.UTF_8);
    }

    private static String runKmax(Path sourcePath, Path composerTmpDir) throws IOException, ComposerException {
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
        int exitCode;
        try {
            Process process = processBuilder.start();
            output = IOUtils.toString(process.getInputStream(), Charset.defaultCharset());
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException("run-kmax.py was interrupted", e);
        }
        if (exitCode != 0) {
            throw new ComposerException("run-kmax.py exited with code " + exitCode);
        }
        return output;
    }

    private void processKmaxOutput(String output, Path sourcePath) throws JsonProcessingException, ComposerException {
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
                filePresenceConditions.put(path, smtLibConverter.convert(entry.getValue()));
            } catch (ParseException e) {
                throw new ComposerException(
                    "Could not parse presence condition (%s at %d). Text was:%n%s"
                        .formatted(entry.getKey(), e.getErrorOffset(), entry.getValue()),
                    e
                );
            }
        }
    }

    public KbuildFeatureMapper createFeatureMapper(Map<Path, GenerationInformation> generationInformationMap) {
        return new KbuildFeatureMapper(this.filePresenceConditions, generationInformationMap);
    }
}
