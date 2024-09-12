package edu.kit.varijoern.caching;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.impl.FeatureModel;
import edu.kit.varijoern.analyzers.AnalysisResult;
import edu.kit.varijoern.analyzers.joern.JoernAnalysisResult;
import edu.kit.varijoern.analyzers.joern.JoernFinding;
import edu.kit.varijoern.composers.sourcemap.SourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.prop4j.And;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SimpleResultCacheTest {
    @Test
    void cachesFeatureModel(@TempDir Path cacheDir) throws IOException {
        SimpleResultCache cache = new SimpleResultCache(cacheDir);
        assertNull(cache.getFeatureModel());
        IFeatureModel featureModel = new FeatureModel("");
        featureModel.createDefaultValues("project");
        cache.cacheFeatureModel(featureModel);
        IFeatureModel cachedFeatureModel = cache.getFeatureModel();
        assertNotNull(cachedFeatureModel);
        assertIterableEquals(featureModel.getFeatures(), cachedFeatureModel.getFeatures());
    }

    @Test
    void cachesSample(@TempDir Path cacheDir) throws IOException {
        SimpleResultCache cache = new SimpleResultCache(cacheDir);
        assertNull(cache.getSample(0));
        List<Map<String, Boolean>> sample = List.of(Map.of("F1", true), Map.of("F1", false));
        cache.cacheSample(sample, 0);
        List<Map<String, Boolean>> cachedSample = cache.getSample(0);
        assertEquals(sample, cachedSample);
    }

    @Test
    void cachesAnalysisResult(@TempDir Path cacheDir) throws IOException {
        SimpleResultCache cache = new SimpleResultCache(cacheDir);
        assertNull(cache.getAnalysisResult(0, 0, JoernAnalysisResult.class));

        Map<String, Boolean> configuration00 = Map.of("F1", true, "F2", false);
        AnalysisResult<JoernFinding> analysisResult00 = new JoernAnalysisResult(List.of(), configuration00);
        cache.cacheAnalysisResult(analysisResult00, 0, 0);

        Map<String, Boolean> configuration01 = Map.of("F1", false, "F2", true);
        AnalysisResult<JoernFinding> analysisResult01 = new JoernAnalysisResult(
                List.of(new JoernFinding("test", "title", "description", 1, Set.of(), null)),
                configuration01);
        cache.cacheAnalysisResult(analysisResult01, 0, 1);

        Map<String, Boolean> configuration10 = Map.of("F1", true, "F2", true);
        AnalysisResult<JoernFinding> analysisResult10 = new JoernAnalysisResult(
                List.of(new JoernFinding(
                                "test2",
                                "title2",
                                "description2",
                                1,
                                Set.of(new SourceLocation(Path.of("src/main.c"), 40)),
                                new And()
                        )
                ),
                configuration10);
        cache.cacheAnalysisResult(analysisResult10, 1, 0);

        AnalysisResult<JoernFinding> cachedAnalysisResult00
                = cache.getAnalysisResult(0, 0, JoernAnalysisResult.class);
        assertEquals(analysisResult00, cachedAnalysisResult00);

        AnalysisResult<JoernFinding> cachedAnalysisResult01
                = cache.getAnalysisResult(0, 1, JoernAnalysisResult.class);
        assertEquals(analysisResult01, cachedAnalysisResult01);

        AnalysisResult<JoernFinding> cachedAnalysisResult10
                = cache.getAnalysisResult(1, 0, JoernAnalysisResult.class);
        assertEquals(analysisResult10, cachedAnalysisResult10);
    }

    @Test
    void invalidatesAnalysisResultsWithoutSample(@TempDir Path cacheDir) throws IOException {
        SimpleResultCache cache = new SimpleResultCache(cacheDir);

        Map<String, Boolean> configuration00 = Map.of("F1", true, "F2", false);
        cache.cacheSample(List.of(configuration00), 0);
        AnalysisResult<JoernFinding> analysisResult00 = new JoernAnalysisResult(List.of(), configuration00);
        cache.cacheAnalysisResult(analysisResult00, 0, 0);

        Map<String, Boolean> configuration10 = Map.of("F1", false, "F2", true);
        AnalysisResult<JoernFinding> analysisResult10 = new JoernAnalysisResult(
                List.of(new JoernFinding("test", "title", "description", 1, Set.of(), null)),
                configuration10);
        cache.cacheAnalysisResult(analysisResult10, 1, 0);

        Map<String, Boolean> configuration11 = Map.of("F1", true, "F2", true);
        AnalysisResult<JoernFinding> analysisResult11 = new JoernAnalysisResult(
                List.of(
                        new JoernFinding(
                                "test2",
                                "title2",
                                "description2",
                                1,
                                Set.of(new SourceLocation(Path.of("src/main.c"), 42)),
                                new And()
                        )
                ),
                configuration11);
        cache.cacheAnalysisResult(analysisResult11, 1, 1);

        cache = new SimpleResultCache(cacheDir);
        List<Map<String, Boolean>> cachedSample0 = cache.getSample(0);
        assertEquals(List.of(configuration00), cachedSample0);
        AnalysisResult<JoernFinding> cachedAnalysisResult00
                = cache.getAnalysisResult(0, 0, JoernAnalysisResult.class);
        assertEquals(analysisResult00, cachedAnalysisResult00);
        assertNull(cache.getSample(1));
        assertNull(cache.getAnalysisResult(1, 0, JoernAnalysisResult.class));
        assertNull(cache.getAnalysisResult(1, 1, JoernAnalysisResult.class));
    }
}
