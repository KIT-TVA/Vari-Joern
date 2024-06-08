package edu.kit.varijoern;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.analyzers.AnalysisResult;
import edu.kit.varijoern.analyzers.Analyzer;
import edu.kit.varijoern.analyzers.AnalyzerConfigFactory;
import edu.kit.varijoern.analyzers.AnalyzerFailureException;
import edu.kit.varijoern.composers.Composer;
import edu.kit.varijoern.composers.ComposerConfigFactory;
import edu.kit.varijoern.composers.ComposerException;
import edu.kit.varijoern.composers.CompositionInformation;
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

public class Main {
    private static final int STATUS_COMMAND_LINE_USAGE_ERROR = 64;
    private static final int STATUS_INVALID_CONFIG = 78;
    private static final int STATUS_IO_ERROR = 74;
    private static final int STATUS_INTERNAL_ERROR = 70;
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
        Path analyzerTmpDirectory;
        Path composerTmpDirectory;
        Path featureModelReaderTmpDirectory;
        try {
            tmpDir = Files.createTempDirectory("vari-joern");
            analyzerTmpDirectory = tmpDir.resolve("analyzer");
            Files.createDirectories(analyzerTmpDirectory);
            composerTmpDirectory = tmpDir.resolve("composer");
            Files.createDirectories(composerTmpDirectory);
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
        Composer composer;
        try {
            composer = config.getComposerConfig().newComposer(composerTmpDirectory);
        } catch (IOException e) {
            LOGGER.atFatal().withThrowable(e).log("Failed to instantiate composer:");
            return STATUS_IO_ERROR;
        } catch (ComposerException e) {
            LOGGER.atFatal().withThrowable(e).log("Failed to instantiate composer:");
            return STATUS_INTERNAL_ERROR;
        }
        Analyzer analyzer;
        try {
            analyzer = config.getAnalyzerConfig().newAnalyzer(analyzerTmpDirectory);
        } catch (IOException e) {
            LOGGER.atFatal().withThrowable(e).log("Failed to instantiate analyzer:");
            return STATUS_IO_ERROR;
        }

        List<AnalysisResult> allAnalysisResults = new ArrayList<>();
        List<AnalysisResult> iterationAnalysisResults = List.of();
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
            iterationAnalysisResults = new ArrayList<>();
            for (int j = 0; j < sample.size(); j++) {
                Map<String, Boolean> features = sample.get(j);
                LOGGER.info("Analyzing variant with features {}", features);
                String destinationDirectoryName = String.format("%d-%d", i, j);
                Path composerDestination = tmpDir.resolve(destinationDirectoryName);
                try {
                    Files.createDirectories(composerDestination);
                } catch (IOException e) {
                    LOGGER.atFatal().withThrowable(e).log("Failed to create composer destination directory");
                    return STATUS_IO_ERROR;
                }

                CompositionInformation composedSourceLocation;
                try {
                    composedSourceLocation = composer.compose(features, composerDestination, featureModel);
                } catch (IOException e) {
                    LOGGER.atFatal().withThrowable(e).log("An IO error occurred:");
                    return STATUS_IO_ERROR;
                } catch (ComposerException e) {
                    LOGGER.atFatal().withThrowable(e).log("A composer error occurred:");
                    return STATUS_INTERNAL_ERROR;
                }

                try {
                    iterationAnalysisResults.add(analyzer.analyze(composedSourceLocation));
                } catch (IOException e) {
                    LOGGER.atFatal().withThrowable(e).log("An I/O error occurred while running the analyzer");
                    return STATUS_IO_ERROR;
                } catch (AnalyzerFailureException e) {
                    LOGGER.atFatal().withThrowable(e).log("The analysis did not complete successfully.");
                    return STATUS_INTERNAL_ERROR;
                }
            }
            allAnalysisResults.addAll(iterationAnalysisResults);
        }

        try {
            args.getResultOutputArgs().getFormatter().printResults(
                    new OutputData(allAnalysisResults, analyzer.aggregateResults()),
                    args.getResultOutputArgs().getDestination().getStream()
            );
        } catch (IOException e) {
            LOGGER.atError().withThrowable(e).log("Failed to print results");
            return STATUS_IO_ERROR;
        }
        return 0;
    }
}