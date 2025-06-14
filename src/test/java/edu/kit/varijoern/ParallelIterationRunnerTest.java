package edu.kit.varijoern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.impl.FeatureModel;
import edu.kit.varijoern.analyzers.*;
import edu.kit.varijoern.caching.DummyResultCache;
import edu.kit.varijoern.caching.ResultCache;
import edu.kit.varijoern.caching.SimpleResultCache;
import edu.kit.varijoern.composers.Composer;
import edu.kit.varijoern.composers.ComposerConfig;
import edu.kit.varijoern.composers.ComposerException;
import edu.kit.varijoern.composers.CompositionInformation;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ParallelIterationRunnerTest {

    @Test
    void pipelineWorks(@TempDir Path tmpDir) throws RunnerException, InterruptedException {
        List<Map<String, Boolean>> sample = List.of(Map.of("F1", true), Map.of("F2", true));
        ParallelIterationRunner runner = new ParallelIterationRunner(2, 1, 1,
                new ComposerConfigMock(false, false, false, -1), new AnalyzerConfigMock(),
                new FeatureModel("test"), new DummyResultCache(), tmpDir);
        ParallelIterationRunner.Output result = runner.run(sample);
        assertNotNull(result.results());
        assertTrue(result.results().stream()
                .anyMatch(analysisResult -> analysisResult.getEnabledFeatures().containsKey("F1")));
        assertTrue(result.results().stream()
                .anyMatch(analysisResult -> analysisResult.getEnabledFeatures().containsKey("F2")));
        runner.stop();
    }

    @Test
    void manyConfigurations(@TempDir Path tmpDir) throws RunnerException, InterruptedException {
        List<Map<String, Boolean>> sample = IntStream.range(0, 100)
                .mapToObj(i -> Map.of("F" + i, true))
                .toList();
        ParallelIterationRunner runner = new ParallelIterationRunner(3, 2, 1,
                new ComposerConfigMock(false, false, false, -1), new AnalyzerConfigMock(),
                new FeatureModel("test"), new DummyResultCache(), tmpDir);
        ParallelIterationRunner.Output result = runner.run(sample);
        assertNotNull(result.results());
        for (int i = 0; i < 100; i++) {
            int capturedI = i;
            assertTrue(result.results().stream()
                    .anyMatch(analysisResult -> analysisResult.getEnabledFeatures().containsKey("F" + capturedI)));
        }
        runner.stop();
    }

    @Test
    void interruption(@TempDir Path tmpDir) throws RunnerException, InterruptedException, BrokenBarrierException {
        List<Map<String, Boolean>> sample = List.of(Map.of("F1", true), Map.of("F2", true));
        ComposerConfigMock composerConfig = new ComposerConfigMock(true, false, true, -1);
        ParallelIterationRunner runner = new ParallelIterationRunner(1, 1, 1, composerConfig,
                new AnalyzerConfigMock(), new FeatureModel("test"), new DummyResultCache(), tmpDir);
        Thread runnerThread = new Thread(() -> {
            try {
                runner.run(sample);
            } catch (InterruptedException e) {
                // expected
            }
        });
        runnerThread.start();
        composerConfig.composers.get(0).startBarrier.await();
        runnerThread.interrupt();
        runnerThread.join();
        assertTrue(runner.isStopped());
        assertFalse(composerConfig.composers.get(0).thread.isAlive());
        assertTrue(composerConfig.composers.get(0).wasInterrupted);
    }

    @Test
    void ignoredInterruption(@TempDir Path tmpDir)
            throws RunnerException, InterruptedException, BrokenBarrierException {
        List<Map<String, Boolean>> sample = List.of(Map.of("F1", true), Map.of("F2", true));
        ComposerConfigMock composerConfig = new ComposerConfigMock(true, true, true, -1);
        ParallelIterationRunner runner = new ParallelIterationRunner(1, 1, 1, composerConfig,
                new AnalyzerConfigMock(), new FeatureModel("test"), new DummyResultCache(), tmpDir);
        Thread runnerThread = new Thread(() -> {
            try {
                runner.run(sample);
            } catch (InterruptedException e) {
                // expected
            }
        });
        runnerThread.start();
        composerConfig.composers.get(0).startBarrier.await();
        runnerThread.interrupt();
        runnerThread.join();
        assertTrue(runner.isStopped());
        assertFalse(composerConfig.composers.get(0).thread.isAlive());
    }

    @Test
    void usesCache(@TempDir Path tmpDir, @TempDir Path cacheDir)
            throws Exception {
        List<Map<String, Boolean>> sample = List.of(Map.of("F1", true), Map.of("F1", false));
        ResultCache cache = new SimpleResultCache(cacheDir);
        cache.cacheSample(sample, 0);

        Function<AnalyzerConfig<?, ?>, ParallelIterationRunner> runnerCreator = (analyzerConfig) -> {
            try {
                return new ParallelIterationRunner(1, 1, 1,
                        new ComposerConfigMock(false, false, false, 1), analyzerConfig,
                        new FeatureModel("test"), cache, tmpDir);
            } catch (RunnerException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        ParallelIterationRunner runner = runnerCreator.apply(new AnalyzerConfigMock());
        ParallelIterationRunner.Output result = runner.run(sample);
        assertEquals(ParallelIterationRunner.Output.error(Main.STATUS_INTERNAL_ERROR), result);

        EmptyAnalysisResult cachedAnalysisResult = cache.getAnalysisResult(0, 0, EmptyAnalysisResult.class);
        assertNotNull(cachedAnalysisResult);
        assertEquals(sample.get(0), cachedAnalysisResult.getEnabledFeatures());
        assertNull(cache.getAnalysisResult(0, 1, EmptyAnalysisResult.class));

        AnalyzerConfigMock analyzerConfig = new AnalyzerConfigMock();
        runner = runnerCreator.apply(analyzerConfig);
        result = runner.run(sample);
        assertNotNull(result.results());

        assertEquals(new HashSet<>(sample), result.results().stream()
                .map(AnalysisResult::getEnabledFeatures)
                .collect(Collectors.toSet()));

        // Verify that the cached results are correct. In particular, result 0 must not have been overwritten.
        EmptyAnalysisResult cachedAnalysisResult0 = cache.getAnalysisResult(0, 0, EmptyAnalysisResult.class);
        assertNotNull(cachedAnalysisResult0);
        assertEquals(sample.get(0), cachedAnalysisResult0.getEnabledFeatures());
        EmptyAnalysisResult cachedAnalysisResult1 = cache.getAnalysisResult(0, 1, EmptyAnalysisResult.class);
        assertNotNull(cachedAnalysisResult1);
        assertEquals(sample.get(1), cachedAnalysisResult1.getEnabledFeatures());

        assertEquals(2, ((EmptyAnalysisResultAggregator) analyzerConfig.getResultAggregator()).getResults().size());
    }

    private static final class ComposerMock implements Composer {
        public final CyclicBarrier startBarrier = new CyclicBarrier(2);
        private final boolean shouldWait;
        private final boolean ignoreInterruption;
        private final boolean useBarrier;
        private boolean wasInterrupted = false;
        private int failAfter;
        private Thread thread;

        private ComposerMock(boolean shouldWait, boolean ignoreInterruption, boolean useBarrier, int failAfter) {
            this.shouldWait = shouldWait;
            this.ignoreInterruption = ignoreInterruption;
            this.useBarrier = useBarrier;
            this.failAfter = failAfter;
        }

        @Override
        public @NotNull CompositionInformation compose(@NotNull Map<String, Boolean> features,
                                                       @NotNull Path destination,
                                                       @NotNull IFeatureModel featureModel)
                throws InterruptedException, ComposerException {
            thread = Thread.currentThread();
            if (useBarrier) {
                try {
                    startBarrier.await();
                } catch (BrokenBarrierException e) {
                    throw new RuntimeException(e);
                }
            }

            if (failAfter == 0) {
                throw new ComposerException("Planned failure");
            } else {
                failAfter--;
            }

            if (shouldWait) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    wasInterrupted = true;
                    if (!ignoreInterruption) {
                        throw e;
                    }
                }
            }
            return new CompositionInformation(
                    Path.of("/nowhere"),
                    features,
                    (file, lineNumber) -> Optional.empty(),
                    location -> Optional.empty(),
                    List.of()
            );
        }
    }

    private static final class ComposerConfigMock extends ComposerConfig {
        private final List<ComposerMock> composers = new ArrayList<>();
        private final boolean shouldWait;
        private final boolean ignoreInterruption;
        private final boolean useBarrier;
        private final int failAfter;

        private ComposerConfigMock(boolean shouldWait, boolean ignoreInterruption, boolean useBarrier, int failAfter) {
            super("mock");
            this.shouldWait = shouldWait;
            this.ignoreInterruption = ignoreInterruption;
            this.useBarrier = useBarrier;
            this.failAfter = failAfter;
        }

        @Override
        public @NotNull Composer newComposer(@NotNull Path tmpPath)
                throws IOException, ComposerException, InterruptedException {
            ComposerMock composer = new ComposerMock(shouldWait, ignoreInterruption, useBarrier, failAfter);
            composers.add(composer);
            return composer;
        }

        public List<ComposerMock> getComposers() {
            return composers;
        }
    }

    private static final class AnalyzerMock implements Analyzer {
        private final ResultAggregator<EmptyAnalysisResult, Finding> resultAggregator;

        private AnalyzerMock(ResultAggregator<EmptyAnalysisResult, Finding> resultAggregator) {
            this.resultAggregator = resultAggregator;
        }

        @Override
        public @NotNull EmptyAnalysisResult analyze(@NotNull CompositionInformation compositionInformation)
                throws InterruptedException {
            EmptyAnalysisResult result = new EmptyAnalysisResult(compositionInformation.getEnabledFeatures());
            this.resultAggregator.addResult(result);
            return result;
        }
    }

    private static final class AnalyzerConfigMock extends AnalyzerConfig<EmptyAnalysisResult, Finding> {
        private final List<AnalyzerMock> analyzers = new ArrayList<>();

        private AnalyzerConfigMock() {
            super("mock", new EmptyAnalysisResultAggregator());
        }

        @Override
        public @NotNull AnalyzerMock newAnalyzer(@NotNull Path tmpPath) {
            AnalyzerMock analyzer = new AnalyzerMock(this.getResultAggregator());
            analyzers.add(analyzer);
            return analyzer;
        }

        public List<AnalyzerMock> getAnalyzers() {
            return analyzers;
        }
    }

    private static class EmptyAnalysisResult extends AnalysisResult<Finding> {
        @JsonCreator
        public EmptyAnalysisResult(@JsonProperty("enabledFeatures") Map<String, Boolean> enabledFeatures) {
            super(enabledFeatures);
        }

        @Override
        public @NotNull List<Finding> getFindings() {
            return List.of();
        }
    }

    private static class EmptyAnalysisResultAggregator extends ResultAggregator<EmptyAnalysisResult, Finding> {
        @Override
        public @NotNull AggregatedAnalysisResult aggregateResults() {
            return new AggregatedAnalysisResult(Set.of());
        }

        @Override
        protected Class<EmptyAnalysisResult> getResultClass() {
            return EmptyAnalysisResult.class;
        }

        public List<EmptyAnalysisResult> getResults() {
            return results;
        }
    }
}
