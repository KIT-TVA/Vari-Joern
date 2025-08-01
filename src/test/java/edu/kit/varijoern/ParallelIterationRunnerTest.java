package edu.kit.varijoern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.impl.FeatureModel;
import edu.kit.varijoern.analyzers.AggregatedAnalysisResult;
import edu.kit.varijoern.analyzers.AnalysisResult;
import edu.kit.varijoern.analyzers.Analyzer;
import edu.kit.varijoern.analyzers.AnalyzerConfig;
import edu.kit.varijoern.analyzers.Finding;
import edu.kit.varijoern.analyzers.ResultAggregator;
import edu.kit.varijoern.caching.DummyResultCache;
import edu.kit.varijoern.caching.ResultCache;
import edu.kit.varijoern.caching.SimpleResultCache;
import edu.kit.varijoern.composers.Composer;
import edu.kit.varijoern.composers.ComposerConfig;
import edu.kit.varijoern.composers.ComposerException;
import edu.kit.varijoern.composers.CompositionInformation;
import edu.kit.varijoern.samplers.Configuration;
import edu.kit.varijoern.samplers.SampleTracker;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void pipelineWorks(boolean sequential, @TempDir Path tmpDir) throws RunnerException, InterruptedException {
        SampleTracker sampleTracker = new SampleTracker();
        List<Configuration> sample = sampleTracker.trackConfigurations(
                List.of(Map.of("F1", true), Map.of("F2", true))
        );
        ParallelIterationRunner runner = new ParallelIterationRunner(2, 1, 1, sequential,
                new ComposerConfigMock(false, false, false, -1), new AnalyzerConfigMock(),
                new FeatureModel("test"), new DummyResultCache(), tmpDir);
        ParallelIterationRunner.Output result = runner.run(sample, sampleTracker);
        assertNotNull(result.results());
        assertTrue(result.results().stream()
                .anyMatch(analysisResult -> analysisResult.getConfiguration().enabledFeatures().containsKey("F1")));
        assertTrue(result.results().stream()
                .anyMatch(analysisResult -> analysisResult.getConfiguration().enabledFeatures().containsKey("F2")));
        runner.stop();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void manyConfigurations(boolean sequential, @TempDir Path tmpDir) throws RunnerException, InterruptedException {
        SampleTracker sampleTracker = new SampleTracker();
        List<Configuration> sample = sampleTracker.trackConfigurations(
                IntStream.range(0, 100)
                        .mapToObj(i -> Map.of("F" + i, true))
                        .toList()
        );
        ParallelIterationRunner runner = new ParallelIterationRunner(3, 2, 1, sequential,
                new ComposerConfigMock(false, false, false, -1), new AnalyzerConfigMock(),
                new FeatureModel("test"), new DummyResultCache(), tmpDir);
        ParallelIterationRunner.Output result = runner.run(sample, sampleTracker);
        assertNotNull(result.results());
        for (int i = 0; i < 100; i++) {
            int capturedI = i;
            assertTrue(result.results().stream()
                    .anyMatch(analysisResult -> analysisResult.getConfiguration().enabledFeatures()
                            .containsKey("F" + capturedI)));
        }
        runner.stop();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void interruption(boolean sequential, @TempDir Path tmpDir) throws RunnerException, InterruptedException, BrokenBarrierException {
        SampleTracker sampleTracker = new SampleTracker();
        List<Configuration> sample = sampleTracker.trackConfigurations(
                List.of(Map.of("F1", true), Map.of("F2", true))
        );
        ComposerConfigMock composerConfig = new ComposerConfigMock(true, false, true, -1);
        ParallelIterationRunner runner = new ParallelIterationRunner(1, 1, 1, sequential, composerConfig,
                new AnalyzerConfigMock(), new FeatureModel("test"), new DummyResultCache(), tmpDir);
        Thread runnerThread = new Thread(() -> {
            try {
                runner.run(sample, sampleTracker);
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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void ignoredInterruption(boolean sequential, @TempDir Path tmpDir)
            throws RunnerException, InterruptedException, BrokenBarrierException {
        SampleTracker sampleTracker = new SampleTracker();
        List<Configuration> sample = sampleTracker.trackConfigurations(
                List.of(Map.of("F1", true), Map.of("F2", true))
        );
        ComposerConfigMock composerConfig = new ComposerConfigMock(true, true, true, -1);
        ParallelIterationRunner runner = new ParallelIterationRunner(1, 1, 1, sequential, composerConfig,
                new AnalyzerConfigMock(), new FeatureModel("test"), new DummyResultCache(), tmpDir);
        Thread runnerThread = new Thread(() -> {
            try {
                runner.run(sample, sampleTracker);
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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void usesCache(boolean sequential, @TempDir Path tmpDir, @TempDir Path cacheDir)
            throws Exception {
        SampleTracker sampleTracker = new SampleTracker();
        @NotNull List<Configuration> sample = sampleTracker.trackConfigurations(
                List.of(Map.of("F1", true), Map.of("F1", false))
        );
        ResultCache cache = new SimpleResultCache(cacheDir);
        cache.cacheSampleConfigurations(sample, 0);

        Function<AnalyzerConfig<?, ?>, ParallelIterationRunner> runnerCreator = (analyzerConfig) -> {
            try {
                return new ParallelIterationRunner(1, 1, 1, sequential,
                        new ComposerConfigMock(false, false, false, 1), analyzerConfig,
                        new FeatureModel("test"), cache, tmpDir);
            } catch (RunnerException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        ParallelIterationRunner runner = runnerCreator.apply(new AnalyzerConfigMock());
        ParallelIterationRunner.Output result = runner.run(sample, sampleTracker);
        assertEquals(ParallelIterationRunner.Output.error(Main.STATUS_INTERNAL_ERROR), result);

        EmptyAnalysisResult cachedAnalysisResult = cache.getAnalysisResult(0, 0, sampleTracker,
                EmptyAnalysisResult.class);
        assertNotNull(cachedAnalysisResult);
        assertEquals(sample.get(0), cachedAnalysisResult.getConfiguration());
        assertNull(cache.getAnalysisResult(0, 1, sampleTracker, EmptyAnalysisResult.class));

        AnalyzerConfigMock analyzerConfig = new AnalyzerConfigMock();
        runner = runnerCreator.apply(analyzerConfig);
        result = runner.run(sample, sampleTracker);
        assertNotNull(result.results());

        assertEquals(new HashSet<>(sample), result.results().stream()
                .map(AnalysisResult::getConfiguration)
                .collect(Collectors.toSet()));

        // Verify that the cached results are correct. In particular, result 0 must not have been overwritten.
        EmptyAnalysisResult cachedAnalysisResult0 = cache.getAnalysisResult(0, 0, sampleTracker,
                EmptyAnalysisResult.class);
        assertNotNull(cachedAnalysisResult0);
        assertEquals(sample.get(0), cachedAnalysisResult0.getConfiguration());
        EmptyAnalysisResult cachedAnalysisResult1 = cache.getAnalysisResult(0, 1, sampleTracker,
                EmptyAnalysisResult.class);
        assertNotNull(cachedAnalysisResult1);
        assertEquals(sample.get(1), cachedAnalysisResult1.getConfiguration());

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
        public @NotNull CompositionInformation compose(@NotNull Configuration configuration,
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
                    configuration,
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
            EmptyAnalysisResult result = new EmptyAnalysisResult(compositionInformation.getConfiguration());
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
        public EmptyAnalysisResult(@JsonProperty("configuration") Configuration configuration) {
            super(configuration);
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
