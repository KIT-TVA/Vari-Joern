package edu.kit.varijoern;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.impl.FeatureModel;
import edu.kit.varijoern.analyzers.*;
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
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ParallelIterationRunnerTest {

    @Test
    void pipelineWorks(@TempDir Path tmpDir) throws RunnerException, InterruptedException {
        List<Map<String, Boolean>> sample = List.of(Map.of("F1", true), Map.of("F2", true));
        ParallelIterationRunner runner = new ParallelIterationRunner(2, 1, 1,
                new ComposerConfigMock(false, false, false), new AnalyzerConfigMock(),
                new FeatureModel("test"), tmpDir);
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
                new ComposerConfigMock(false, false, false), new AnalyzerConfigMock(),
                new FeatureModel("test"), tmpDir);
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
        ComposerConfigMock composerConfig = new ComposerConfigMock(true, false, true);
        ParallelIterationRunner runner = new ParallelIterationRunner(1, 1, 1, composerConfig,
                new AnalyzerConfigMock(), new FeatureModel("test"), tmpDir);
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
        ComposerConfigMock composerConfig = new ComposerConfigMock(true, true, true);
        ParallelIterationRunner runner = new ParallelIterationRunner(1, 1, 1, composerConfig,
                new AnalyzerConfigMock(), new FeatureModel("test"), tmpDir);
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

    private static final class ComposerMock implements Composer {
        public final CyclicBarrier startBarrier = new CyclicBarrier(2);
        private final boolean shouldWait;
        private final boolean ignoreInterruption;
        private final boolean useBarrier;
        private boolean wasInterrupted = false;
        private Thread thread;

        private ComposerMock(boolean shouldWait, boolean ignoreInterruption, boolean useBarrier) {
            this.shouldWait = shouldWait;
            this.ignoreInterruption = ignoreInterruption;
            this.useBarrier = useBarrier;
        }

        @Override
        public @NotNull CompositionInformation compose(@NotNull Map<String, Boolean> features,
                                                       @NotNull Path destination,
                                                       @NotNull IFeatureModel featureModel)
                throws InterruptedException {
            thread = Thread.currentThread();
            if (useBarrier) {
                try {
                    startBarrier.await();
                } catch (BrokenBarrierException e) {
                    throw new RuntimeException(e);
                }
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
        private boolean useBarrier;

        private ComposerConfigMock(boolean shouldWait, boolean ignoreInterruption, boolean useBarrier) {
            super("mock");
            this.shouldWait = shouldWait;
            this.ignoreInterruption = ignoreInterruption;
            this.useBarrier = useBarrier;
        }

        @Override
        public @NotNull Composer newComposer(@NotNull Path tmpPath)
                throws IOException, ComposerException, InterruptedException {
            ComposerMock composer = new ComposerMock(shouldWait, ignoreInterruption, useBarrier);
            composers.add(composer);
            return composer;
        }

        public List<ComposerMock> getComposers() {
            return composers;
        }
    }

    private static final class AnalyzerMock implements Analyzer<EmptyAnalysisResult> {
        @Override
        public @NotNull EmptyAnalysisResult analyze(@NotNull CompositionInformation compositionInformation)
                throws InterruptedException {
            return new EmptyAnalysisResult(compositionInformation.getEnabledFeatures());
        }
    }

    private static final class AnalyzerConfigMock extends AnalyzerConfig<EmptyAnalysisResult> {
        private final List<AnalyzerMock> analyzers = new ArrayList<>();

        private AnalyzerConfigMock() {
            super("mock", new EmptyAnalysisResultAggregator());
        }

        @Override
        public @NotNull AnalyzerMock newAnalyzer(@NotNull Path tmpPath) {
            AnalyzerMock analyzer = new AnalyzerMock();
            analyzers.add(analyzer);
            return analyzer;
        }

        public List<AnalyzerMock> getAnalyzers() {
            return analyzers;
        }
    }

    private static class EmptyAnalysisResult extends AnalysisResult {
        public EmptyAnalysisResult(Map<String, Boolean> enabledFeatures) {
            super(enabledFeatures, (file, lineNumber) -> Optional.empty(), location -> Optional.empty());
        }

        @Override
        public @NotNull List<AnnotatedFinding> getFindings() {
            return List.of();
        }
    }

    private static class EmptyAnalysisResultAggregator extends ResultAggregator<EmptyAnalysisResult> {
        @Override
        public @NotNull AggregatedAnalysisResult aggregateResults() {
            return new AggregatedAnalysisResult(Set.of());
        }
    }
}