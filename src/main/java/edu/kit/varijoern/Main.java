package edu.kit.varijoern;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.analyzers.AnalysisResult;
import edu.kit.varijoern.analyzers.AnalyzerConfigFactory;
import edu.kit.varijoern.analyzers.ResultAggregator;
import edu.kit.varijoern.composers.ComposerConfigFactory;
import edu.kit.varijoern.config.Config;
import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.featuremodel.FeatureModelReader;
import edu.kit.varijoern.featuremodel.FeatureModelReaderConfigFactory;
import edu.kit.varijoern.featuremodel.FeatureModelReaderException;
import edu.kit.varijoern.output.OutputData;
import edu.kit.varijoern.samplers.Sampler;
import edu.kit.varijoern.samplers.SamplerConfigFactory;
import edu.kit.varijoern.samplers.SamplerException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Main {
    public static final int STATUS_OK = 0;
    public static final int STATUS_COMMAND_LINE_USAGE_ERROR = 64;
    public static final int STATUS_INVALID_CONFIG = 78;
    public static final int STATUS_IO_ERROR = 74;
    public static final int STATUS_INTERNAL_ERROR = 70;
    private static final Logger LOGGER = LogManager.getLogger();

    public static void main(String[] args) {
        Args parsedArgs = new Args();
        JCommander.Builder jcommanderBuilder = JCommander.newBuilder()
                .addObject(parsedArgs);
        addComponentArgs(jcommanderBuilder);
        JCommander jCommander = jcommanderBuilder
                .build();
        try {
            jCommander.parse(args);
        } catch (ParameterException e) {
            e.usage();
            System.exit(STATUS_COMMAND_LINE_USAGE_ERROR);
        }
        if (parsedArgs.isHelp()) {
            jCommander.usage();
            return;
        }
        String logLevel = "info";
        if (parsedArgs.isTrace()) {
            logLevel = "trace";
        } else if (parsedArgs.isVerbose()) {
            logLevel = "debug";
        }
        Configuration log4jConfig = new XmlConfiguration(LoggerContext.getContext(), ConfigurationSource.fromResource(
                "log4j2.xml", Main.class.getClassLoader()
        ));
        log4jConfig.getProperties().put("level", logLevel);
        Configurator.reconfigure(log4jConfig);

        LOGGER.info("Reading configuration");

        Config config;
        try {
            config = new Config(parsedArgs.getConfig());
        } catch (IOException e) {
            LOGGER.atFatal().withThrowable(e).log("The configuration file could not be read");
            System.exit(STATUS_INVALID_CONFIG);
            return;
        } catch (InvalidConfigException e) {
            LOGGER.atFatal().withThrowable(e).log("The configuration file could not be parsed");
            System.exit(STATUS_INVALID_CONFIG);
            return;
        }
        System.exit(runUsingConfig(config, parsedArgs));
    }

    private static void addComponentArgs(@NotNull JCommander.Builder jcommanderBuilder) {
        for (List<Object> componentArgs : List.of(
                FeatureModelReaderConfigFactory.getComponentArgs(),
                SamplerConfigFactory.getComponentArgs(),
                ComposerConfigFactory.getComponentArgs(),
                AnalyzerConfigFactory.getComponentArgs()
        )) {
            for (Object componentArg : componentArgs) {
                jcommanderBuilder.addObject(componentArg);
            }
        }
    }

    private static int runUsingConfig(@NotNull Config config, @NotNull Args args) {
        Path tmpDir;
        Path featureModelReaderTmpDirectory;
        try {
            tmpDir = Files.createTempDirectory("vari-joern");
            featureModelReaderTmpDirectory = tmpDir.resolve("feature-model-reader");
            Files.createDirectories(featureModelReaderTmpDirectory);
        } catch (IOException e) {
            LOGGER.atFatal().withThrowable(e).log("Failed to create temporary directory");
            return STATUS_IO_ERROR;
        }

        FeatureModelReader featureModelReader = config.getFeatureModelReaderConfig().newFeatureModelReader();
        IFeatureModel featureModel;
        try {
            featureModel = featureModelReader.read(featureModelReaderTmpDirectory);
        } catch (IOException e) {
            LOGGER.atFatal().withThrowable(e).log("An I/O error occurred while reading the feature model");
            return STATUS_IO_ERROR;
        } catch (FeatureModelReaderException e) {
            LOGGER.atFatal().withThrowable(e).log("The feature model could not be read");
            return STATUS_INTERNAL_ERROR;
        }

        Sampler sampler = config.getSamplerConfig().newSampler(featureModel);
        ParallelIterationRunner runner;
        try {
            runner = new ParallelIterationRunner(config.getComposerConfig(),
                    config.getAnalyzerConfig(), featureModel, tmpDir);
        } catch (RunnerException e) {
            LOGGER.atFatal().withThrowable(e).log("Failed to create runner");
            return STATUS_INTERNAL_ERROR;
        }
        ResultAggregator<?> resultAggregator = config.getAnalyzerConfig().getResultAggregator();

        List<AnalysisResult> allAnalysisResults = new ArrayList<>();
        List<AnalysisResult> iterationAnalysisResults = null;
        try {
            for (int i = 0; i < config.getIterations(); i++) {
                LOGGER.info("Iteration {}", i + 1);
                List<Map<String, Boolean>> sample;
                try {
                    sample = sampler.sample(iterationAnalysisResults);
                } catch (SamplerException e) {
                    LOGGER.atFatal().withThrowable(e).log("A sampler error occurred");
                    return STATUS_INTERNAL_ERROR;
                }
                LOGGER.info("Analyzing {} variants", sample.size());
                ParallelIterationRunner.Output runnerOutput;
                try {
                    runnerOutput = runner.run(sample);
                } catch (InterruptedException e) {
                    LOGGER.atFatal().withThrowable(e).log("The runner was interrupted");
                    return STATUS_INTERNAL_ERROR;
                }
                if (runnerOutput.isError()) return Objects.requireNonNull(runnerOutput.exitCode());
                iterationAnalysisResults = Objects.requireNonNull(runnerOutput.results());

                allAnalysisResults.addAll(iterationAnalysisResults);
            }
        } finally {
            try {
                runner.stop();
            } catch (InterruptedException e) {
                LOGGER.atWarn().withThrowable(e).log("Interrupted while closing runner");
            }
        }

        try {
            args.getResultOutputArgs().getFormatter().printResults(
                    new OutputData(allAnalysisResults, resultAggregator.aggregateResults()),
                    args.getResultOutputArgs().getDestination().getStream()
            );
        } catch (IOException e) {
            LOGGER.atError().withThrowable(e).log("Failed to print results");
            return STATUS_IO_ERROR;
        }
        return STATUS_OK;
    }
}