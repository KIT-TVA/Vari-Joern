package edu.kit.varijoern;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.analyzers.*;
import edu.kit.varijoern.caching.ResultCache;
import edu.kit.varijoern.composers.Composer;
import edu.kit.varijoern.composers.ComposerConfig;
import edu.kit.varijoern.composers.ComposerException;
import edu.kit.varijoern.composers.CompositionInformation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * A class that runs iterations in parallel using a pool of composer and analyzer workers. It uses a temporary directory
 * in which it creates subdirectories for each composer ({@code composer0}, {@code composer1}, ...), analyzer
 * ({@code analyzer0}, {@code analyzer1}, ...) and generated variant ({@code 0-0}, {@code 0-1}, ...).
 * <p>
 * This class is thread-safe.
 */
public class ParallelIterationRunner {
    private static final Logger LOGGER = LogManager.getLogger();

    private boolean isStopped = false;

    private final @NotNull IFeatureModel featureModel;
    private final @NotNull ResultAggregator<?, ?> resultAggregator;
    private final @NotNull ResultCache resultCache;
    private final @NotNull Path tmpDir;

    private final @NotNull BlockingDeque<Message<ComposerInvocation>> sampleQueue = new LinkedBlockingDeque<>();
    private final @NotNull BlockingDeque<Message<CompositionInformation>> compositionQueue;
    private final @NotNull BlockingQueue<Message<AnalysisResult<?>>> resultQueue = new LinkedBlockingQueue<>();

    private final @NotNull List<FallibleRunner<?, ?>> runners;
    // Always lock `this` before accessing `iteration`. Currently, this field is only accessed in the `run` method, so
    // there is no need to make it atomic.
    private volatile int iteration = 0;

    /**
     * Creates a new parallel iteration runner.
     *
     * @param numComposers             the number of composer workers
     * @param numAnalyzers             the number of analyzer workers
     * @param compositionQueueCapacity the capacity of the composition queue, i.e., the maximum number of compositions
     *                                 that can the composer workers can queue for analysis
     * @param composerConfig           the configuration for the composer
     * @param analyzerConfig           the configuration for the analyzer
     * @param featureModel             the feature model of the source code
     * @param resultCache              the result cache to store intermediate results
     * @param tmpDir                   the temporary directory to store intermediate results
     * @throws RunnerException      if an error occurs during instantiation
     * @throws InterruptedException if the current thread is interrupted
     */
    public ParallelIterationRunner(int numComposers, int numAnalyzers, int compositionQueueCapacity,
                                   @NotNull ComposerConfig composerConfig, @NotNull AnalyzerConfig<?, ?> analyzerConfig,
                                   @NotNull IFeatureModel featureModel, @NotNull ResultCache resultCache,
                                   @NotNull Path tmpDir)
            throws RunnerException, InterruptedException {
        this.featureModel = featureModel;
        this.resultAggregator = analyzerConfig.getResultAggregator();
        this.resultCache = resultCache;
        this.tmpDir = tmpDir;
        this.compositionQueue = new LinkedBlockingDeque<>(compositionQueueCapacity);

        this.runners = new ArrayList<>(numComposers + numAnalyzers);

        for (int i = 0; i < numComposers; i++) {
            Path composerTmpDirectory = tmpDir.resolve("composer" + i);
            try {
                Files.createDirectories(composerTmpDirectory);
            } catch (IOException e) {
                throw new RunnerException("Failed to create temporary directory for composer", e);
            }
            Composer composer;
            try {
                composer = composerConfig.newComposer(composerTmpDirectory);
            } catch (IOException | ComposerException e) {
                throw new RunnerException("Failed to instantiate composer", e);
            }
            ComposerRunner composerRunner = new ComposerRunner(composer);
            composerRunner.setName("Composer-" + i);
            composerRunner.start();
            runners.add(composerRunner);
        }

        for (int i = 0; i < numAnalyzers; i++) {
            Path analyzerTmpDirectory = tmpDir.resolve("analyzer" + i);
            try {
                Files.createDirectories(analyzerTmpDirectory);
            } catch (IOException e) {
                throw new RunnerException("Failed to create temporary directory for analyzer", e);
            }
            Analyzer analyzer;
            try {
                analyzer = analyzerConfig.newAnalyzer(analyzerTmpDirectory);
            } catch (IOException e) {
                throw new RunnerException("Failed to instantiate analyzer", e);
            }
            AnalyzerRunner analyzerRunner = new AnalyzerRunner(analyzer);
            analyzerRunner.setName("Analyzer-" + i);
            analyzerRunner.start();
            runners.add(analyzerRunner);
        }
    }

    /**
     * Runs a single iteration with the given sample.
     *
     * @param sample the sample to analyze
     * @return the results of the analysis
     * @throws InterruptedException if the runner is interrupted
     */
    public synchronized Output run(@NotNull List<Map<String, Boolean>> sample)
            throws InterruptedException {
        if (isStopped) {
            throw new IllegalStateException("The runner has been stopped.");
        }
        List<AnalysisResult<?>> results = new ArrayList<>(sample.size());
        try {
            int uncachedResults = 0;
            for (int i = 0; i < sample.size(); i++) {
                Map<String, Boolean> features = sample.get(i);
                AnalysisResult<?> cachedResult = this.resultAggregator.tryAddResultFromCache(
                        this.resultCache.getAnalysisResultExtractor(iteration, i));
                if (cachedResult != null) {
                    results.add(cachedResult);
                    continue;
                }
                uncachedResults++;
                sampleQueue.put(Message.of(new ComposerInvocation(features,
                        this.tmpDir.resolve(Path.of(iteration + "-" + i))
                ), i));
            }

            for (int i = 0; i < uncachedResults; i++) {
                Message<AnalysisResult<?>> message;
                do {
                    if (this.checkForDeadWorkers()) {
                        stop();
                        return Output.error(Main.STATUS_INTERNAL_ERROR);
                    }
                    message = resultQueue.poll(1, TimeUnit.SECONDS);
                } while (message == null);
                if (message.isError()) {
                    LOGGER.error("A worker failed. Stopping the runner.");
                    stop();
                    return Output.error(Objects.requireNonNull(message.exitCode()));
                }
                AnalysisResult<?> result = Objects.requireNonNull(message.data());
                this.resultCache.cacheAnalysisResult(result, iteration, message.configurationIndex());
                results.add(result);
            }
        } catch (InterruptedException e) {
            LOGGER.atWarn().withThrowable(e).log("The runner was interrupted");
            stop();
            throw new InterruptedException();
        } finally {
            this.iteration++;
        }
        return Output.of(results);
    }

    private boolean checkForDeadWorkers() {
        for (FallibleRunner<?, ?> runner : runners) {
            if (!runner.isAlive()) {
                LOGGER.atError().withThrowable(runner.throwable).log("A worker thread failed:");
                return true;
            }
        }
        return false;
    }

    /**
     * Stops the runner and waits for all workers to finish.
     *
     * @throws InterruptedException if the runner is interrupted
     */
    public synchronized void stop() throws InterruptedException {
        if (this.isStopped) return;
        this.isStopped = true;

        LOGGER.info("Stopping runner");

        for (FallibleRunner<?, ?> runner : runners) {
            runner.requestTermination();
        }
        for (FallibleRunner<?, ?> runner : runners) {
            runner.join();
        }

        LOGGER.info("Runner stopped");
    }

    /**
     * Returns whether the runner has been stopped.
     *
     * @return {@code true} if the runner has been stopped, {@code false} otherwise
     */
    public boolean isStopped() {
        return this.isStopped;
    }

    private abstract static class FallibleRunner<T, R> extends Thread {
        private final BlockingQueue<Message<T>> inputQueue;
        private final BlockingQueue<Message<R>> outputQueue;
        private volatile @Nullable Throwable throwable = null;
        private volatile boolean shouldStop = false;

        protected FallibleRunner(BlockingQueue<Message<T>> inputQueue, BlockingQueue<Message<R>> outputQueue) {
            this.inputQueue = inputQueue;
            this.outputQueue = outputQueue;
        }

        @Override
        public void run() {
            try {
                while (!this.shouldStop) {
                    if (this.inputQueue.isEmpty()) {
                        LOGGER.debug("Waiting for work");
                    }
                    Message<T> message = this.inputQueue.take();
                    if (message.isError()) {
                        this.outputQueue.put(Message.error(Objects.requireNonNull(message.exitCode()),
                                message.configurationIndex));
                        continue;
                    }
                    Message<R> result = process(message.data(), message.configurationIndex());
                    if (this.outputQueue.remainingCapacity() == 0) {
                        LOGGER.debug("Output queue is full. Waiting for space to become available");
                    }
                    this.outputQueue.put(result);
                }
            } catch (InterruptedException e) {
                LOGGER.atWarn().withThrowable(e).log("Interrupted");
            }
            // CHECKSTYLE:OFF: IllegalCatch
            catch (Throwable t) {
                this.throwable = t;
            }
            // CHECKSTYLE:ON: IllegalCatch
        }

        public void requestTermination() {
            this.shouldStop = true;
            this.interrupt();
        }

        protected abstract Message<R> process(T item, int configurationIndex) throws InterruptedException;
    }

    private final class ComposerRunner extends FallibleRunner<ComposerInvocation, CompositionInformation> {
        private final @NotNull Composer composer;

        private ComposerRunner(@NotNull Composer composer) {
            super(sampleQueue, compositionQueue);
            this.composer = composer;
        }

        @Override
        public Message<CompositionInformation> process(ComposerInvocation arguments, int configurationIndex)
                throws InterruptedException {
            LOGGER.info("Composing variant with features {}", arguments.features);
            try {
                CompositionInformation compositionInformation
                        = this.composer.compose(arguments.features(), arguments.destination(), featureModel);
                return Message.of(compositionInformation, configurationIndex);
            } catch (IOException e) {
                LOGGER.atError().withThrowable(e).log("An IO error occurred:");
                return Message.error(Main.STATUS_IO_ERROR, configurationIndex);
            } catch (ComposerException e) {
                LOGGER.atError().withThrowable(e).log("A composer error occurred:");
                return Message.error(Main.STATUS_INTERNAL_ERROR, configurationIndex);
            }
        }
    }

    private final class AnalyzerRunner extends FallibleRunner<CompositionInformation, AnalysisResult<?>> {
        private final @NotNull Analyzer analyzer;

        private AnalyzerRunner(@NotNull Analyzer analyzer) {
            super(compositionQueue, resultQueue);
            this.analyzer = analyzer;
        }

        @Override
        public Message<AnalysisResult<?>> process(CompositionInformation compositionInformation, int configurationIndex)
                throws InterruptedException {
            LOGGER.info("Analyzing variant with features {}", compositionInformation.getEnabledFeatures());

            try {
                AnalysisResult<?> result = this.analyzer.analyze(compositionInformation);
                return Message.of(result, configurationIndex);
            } catch (IOException e) {
                LOGGER.atError().withThrowable(e).log("An IO error occurred:");
                return Message.error(Main.STATUS_IO_ERROR, configurationIndex);
            } catch (AnalyzerFailureException e) {
                LOGGER.atError().withThrowable(e).log("An analyzer error occurred:");
                return Message.error(Main.STATUS_INTERNAL_ERROR, configurationIndex);
            }
        }
    }

    private record Message<T>(@Nullable T data, int configurationIndex, boolean isError, @Nullable Integer exitCode) {
        public static <T> Message<T> of(@Nullable T data, int configurationIndex) {
            return new Message<>(data, configurationIndex, false, null);
        }

        public static <T> Message<T> error(int exitCode, int configurationIndex) {
            return new Message<>(null, configurationIndex, true, exitCode);
        }
    }

    private record ComposerInvocation(@NotNull Map<String, Boolean> features, @NotNull Path destination) {
    }

    /**
     * Contains the output of the runner. If {@code result} is {@code null}, the runner failed with the given exit code.
     * Otherwise, the runner succeeded and the results are contained in {@code result}.
     *
     * @param results  the results of the analysis
     * @param exitCode the exit code of the runner
     */
    public record Output(@Nullable List<AnalysisResult<?>> results, @Nullable Integer exitCode) {
        public static Output of(@Nullable List<AnalysisResult<?>> results) {
            return new Output(results, null);
        }

        public static Output error(int errorCode) {
            return new Output(null, errorCode);
        }

        public boolean isError() {
            return exitCode != null;
        }
    }
}
