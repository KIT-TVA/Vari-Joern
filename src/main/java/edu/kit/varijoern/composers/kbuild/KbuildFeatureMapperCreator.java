package edu.kit.varijoern.composers.kbuild;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.composers.ComposerException;
import jodd.util.ResourcesUtil;
import org.apache.commons.io.IOUtils;
import org.prop4j.Node;
import xtc.lang.cpp.*;
import xtc.tree.Location;

import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;
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
    public KbuildFeatureMapperCreator(Path sourcePath, String system, Path composerTmpDir, IFeatureModel featureModel)
            throws IOException, ComposerException {
        this.createKmaxallScript(composerTmpDir);
        if (system.equals("busybox")) {
            processKmaxOutput(runKmax(sourcePath, composerTmpDir), sourcePath, featureModel);
        }
        //preprocessorExperiment(sourcePath);
    }

    private static void preprocessorExperiment(Path sourcePath) throws IOException {
        Path filePath = Path.of("/home/erik/variability/JoernExternalDefines/echo.c");
        TokenCreator tokenCreator = new CTokenCreator();
        HeaderFileManager headerFileManager = new HeaderFileManager(
                new FileReader(filePath.toFile()),
                filePath.toFile(),
                List.of(), List.of(), List.of(),
                tokenCreator,
                new StopWatch()
        );
        MacroTable macroTable = new MacroTable(tokenCreator);
        PresenceConditionManager presenceConditionManager = new PresenceConditionManager();
        ConditionEvaluator conditionEvaluator = new ConditionEvaluator(ExpressionParser.fromRats(),
                presenceConditionManager, macroTable);
        Preprocessor preprocessor = new Preprocessor(headerFileManager, macroTable, presenceConditionManager,
                conditionEvaluator, tokenCreator);
        preprocessor.showPresenceConditions(true);
        preprocessor.showErrors(true);
        Syntax next;
        Map<Integer, PresenceConditionManager.PresenceCondition> presenceConditions = new HashMap<>();
        do {
            next = preprocessor.next();
            Location location = headerFileManager.include.getLocation();
            if (location != null && location.file.endsWith("echo.c")) {
                PresenceConditionManager.PresenceCondition presenceCondition = presenceConditionManager.reference();
                PresenceConditionManager.PresenceCondition previousCondition = presenceConditions.get(location.line);
                if (previousCondition == null) {
                    presenceConditions.put(location.line, presenceCondition);
                } else if (!presenceCondition.is(previousCondition)) {
                    System.err.printf("Conflict in line %d between %s and %s%n", location.line, previousCondition,
                            presenceCondition);
                }
            }
        } while (next.kind() != Syntax.Kind.EOF);
        System.err.printf("Presence conditions:%n%s",
                presenceConditions.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> "%d: %s".formatted(e.getKey(), e.getValue()))
                        .collect(Collectors.joining("\n"))
        );

        System.exit(0);
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

    private void processKmaxOutput(String output, Path sourcePath, IFeatureModel featureModel)
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
                        System.err.printf("Option in presence condition does not start with CONFIG_: %s; file: %s%n",
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
                    System.err.println(warning);
                }
                filePresenceConditions.put(path, presenceCondition);
            } catch (ParseException e) {
                throw new ComposerException(
                        "Could not parse presence condition (%s at %d). Text was:%n%s"
                                .formatted(entry.getKey(), e.getErrorOffset(), entry.getValue()),
                        e
                );
            }
        }
    }

    public KbuildFeatureMapper createFeatureMapper(Map<Path, GenerationInformation> generationInformationMap,
                                                   Map<Path, LineFeatureMapper> lineFeatureMappers) {
        return new KbuildFeatureMapper(this.filePresenceConditions, lineFeatureMappers, generationInformationMap);
    }
}
