package edu.kit.varijoern.caching;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.io.manager.FeatureModelManager;
import de.ovgu.featureide.fm.core.io.xml.XmlFeatureModelFormat;
import edu.kit.varijoern.analyzers.AnalysisResult;
import edu.kit.varijoern.featuremodel.FeatureModelReaderException;
import edu.kit.varijoern.featuremodel.featureide.FeatureIDEFMReader;
import edu.kit.varijoern.output.PathSerializer;
import edu.kit.varijoern.serialization.JacksonNodeDeserializer;
import edu.kit.varijoern.serialization.JacksonNodeSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.prop4j.Node;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class SimpleResultCache extends ResultCache {
    private static final Path FEATURE_MODEL_CACHE_FILE = Path.of("featureModel.xml");
    private static final Path SAMPLE_CACHE_FILE = Path.of("sample.json");
    private static final String ANALYSIS_RESULT_CACHE_FILE_FORMAT = "analysisResult_%d.json";
    private static final Logger LOGGER = LogManager.getLogger();

    private final @NotNull Path cacheDir;
    private final @NotNull ObjectMapper objectMapper;

    public SimpleResultCache(@NotNull Path cacheDir) throws IOException {
        this.cacheDir = cacheDir;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new Jdk8Module());
        SimpleModule module = new SimpleModule();
        module.addSerializer(Path.class, new PathSerializer());
        module.addDeserializer(Node.class, new JacksonNodeDeserializer());
        module.addSerializer(Node.class, new JacksonNodeSerializer());
        this.objectMapper.registerModule(module);
        Files.createDirectories(cacheDir);

        // Invalidate analysis results of iterations without readable sample
        try (Stream<Path> files = Files.list(cacheDir)) {
            files.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(fileName -> fileName.matches("\\d+"))
                    .map(Integer::parseInt)
                    .forEach(iteration -> {
                        if (this.getSample(iteration) == null) {
                            LOGGER.debug("Iteration {} has no sample, invalidating analysis results", iteration);
                            this.invalidateAnalysisResults(iteration);
                        }
                    });
        }
    }

    @Override
    public synchronized @Nullable IFeatureModel getFeatureModel() {
        Path featureModelPath = cacheDir.resolve(FEATURE_MODEL_CACHE_FILE);
        if (!Files.exists(featureModelPath)) {
            LOGGER.debug("Feature model cache does not exist");
            return null;
        }
        try {
            LOGGER.debug("Reading feature model from cache");
            return new FeatureIDEFMReader(featureModelPath).read();
        } catch (FeatureModelReaderException e) {
            LOGGER.warn("Failed to read feature model from cache", e);
            return null;
        }
    }

    @Override
    public synchronized void cacheFeatureModel(@NotNull IFeatureModel featureModel) {
        if (!FeatureModelManager.save(featureModel, cacheDir.resolve(FEATURE_MODEL_CACHE_FILE),
                new XmlFeatureModelFormat())) {
            LOGGER.warn("Failed to save feature model to cache");
        } else {
            LOGGER.debug("Feature model saved to cache");
        }
    }

    @Override
    public synchronized @Nullable List<Map<String, Boolean>> getSample(int iteration) {
        Path samplePath = this.getIterationDir(iteration).resolve(SAMPLE_CACHE_FILE);
        if (!Files.exists(samplePath)) {
            LOGGER.debug("Sample cache does not exist");
            return null;
        }
        try {
            LOGGER.debug("Reading sample from cache");
            return objectMapper.readValue(samplePath.toFile(), new TypeReference<>() {
            });
        } catch (IOException e) {
            LOGGER.warn("Failed to read sample from cache", e);
            return null;
        }
    }

    @Override
    public synchronized void cacheSample(@NotNull List<Map<String, Boolean>> sample, int iteration) {
        Path iterationDir = this.getIterationDir(iteration);
        try {
            Files.createDirectories(iterationDir);
            invalidateAnalysisResults(iteration);
            objectMapper.writeValue(iterationDir.resolve(SAMPLE_CACHE_FILE).toFile(), sample);
            LOGGER.debug("Sample saved to cache");
        } catch (IOException e) {
            LOGGER.warn("Failed to save sample to cache", e);
        }
    }

    private void invalidateAnalysisResults(int iteration) {
        Path iterationDir = this.getIterationDir(iteration);
        if (!Files.exists(iterationDir)) {
            return;
        }
        try {
            try (Stream<Path> files = Files.list(iterationDir)) {
                files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().startsWith("analysisResult_"))
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                LOGGER.warn("Failed to delete analysis result cache file", e);
                            }
                        });
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to delete analysis result cache files", e);
        }
    }

    @Override
    public synchronized <T> @Nullable T getAnalysisResult(int iteration, int configurationIndex, Class<T> type) {
        Path analysisResultPath = this.getIterationDir(iteration)
                .resolve(String.format(ANALYSIS_RESULT_CACHE_FILE_FORMAT, configurationIndex));
        if (!Files.exists(analysisResultPath)) {
            LOGGER.debug("Analysis result cache does not exist for configuration {} in iteration {}",
                    iteration, configurationIndex);
            return null;
        }
        try {
            LOGGER.debug("Reading analysis result from cache for configuration {} in iteration {}",
                    iteration, configurationIndex);
            return objectMapper.readValue(analysisResultPath.toFile(), type);
        } catch (IOException e) {
            LOGGER.warn("Failed to read analysis result from cache for configuration {} in iteration {}",
                    iteration, configurationIndex, e);
            return null;
        }
    }

    @Override
    public synchronized void cacheAnalysisResult(@NotNull AnalysisResult<?> result, int iteration,
                                                 int configurationIndex) {
        Path iterationDir = getIterationDir(iteration);
        try {
            Files.createDirectories(iterationDir);
            objectMapper.writeValue(iterationDir.resolve(String.format(ANALYSIS_RESULT_CACHE_FILE_FORMAT,
                    configurationIndex)).toFile(), result);
            LOGGER.debug("Analysis result saved to cache");
        } catch (IOException e) {
            LOGGER.warn("Failed to save analysis result to cache", e);
        }
    }

    private @NotNull Path getIterationDir(int iteration) {
        return cacheDir.resolve(String.valueOf(iteration));
    }
}
