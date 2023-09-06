package edu.kit.varijoern;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.impl.CoreFactoryWorkspaceLoader;
import de.ovgu.featureide.fm.core.base.impl.DefaultFeatureModelFactory;
import de.ovgu.featureide.fm.core.base.impl.FMFactoryManager;
import de.ovgu.featureide.fm.core.base.impl.FMFormatManager;
import de.ovgu.featureide.fm.core.io.manager.FeatureModelManager;
import de.ovgu.featureide.fm.core.io.xml.XmlFeatureModelFormat;
import edu.kit.varijoern.analyzers.AnalysisResult;
import edu.kit.varijoern.analyzers.Analyzer;
import edu.kit.varijoern.analyzers.AnalyzerFailureException;
import edu.kit.varijoern.composers.Composer;
import edu.kit.varijoern.composers.ComposerException;
import edu.kit.varijoern.composers.CompositionInformation;
import edu.kit.varijoern.config.Config;
import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.samplers.Sampler;
import edu.kit.varijoern.samplers.SamplerException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String USAGE = "Usage: vari-joern [config]";
    private static final int STATUS_COMMAND_LINE_USAGE_ERROR = 64;
    private static final int STATUS_INVALID_CONFIG = 78;
    private static final int STATUS_IO_ERROR = 74;
    private static final int STATUS_INTERNAL_ERROR = 70;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println(USAGE);
            System.exit(STATUS_COMMAND_LINE_USAGE_ERROR);
        }

        Config config;
        try {
            config = new Config(Path.of(args[0]));
        } catch (IOException e) {
            System.err.printf("The configuration file could not be read: %s%n", e.getMessage());
            System.exit(STATUS_INVALID_CONFIG);
            return;
        } catch (InvalidConfigException e) {
            System.err.printf("The configuration file could not be parsed:%n%s%n", e.getMessage());
            System.exit(STATUS_INVALID_CONFIG);
            return;
        }
        System.exit(runUsingConfig(config));
    }

    private static int runUsingConfig(Config config) {
        FMFormatManager.getInstance().addExtension(new XmlFeatureModelFormat());
        FMFactoryManager.getInstance().addExtension(new DefaultFeatureModelFactory());
        FMFactoryManager.getInstance().setWorkspaceLoader(new CoreFactoryWorkspaceLoader());
        IFeatureModel featureModel = FeatureModelManager.load(config.getFeatureModelPath());

        Path tmpDir;
        Path analyzerTmpDirectory;
        try {
            tmpDir = Files.createTempDirectory("vari-joern");
            analyzerTmpDirectory = tmpDir.resolve("analyzer");
            Files.createDirectories(analyzerTmpDirectory);
        } catch (IOException e) {
            System.err.printf("Failed to create temporary directory: %s%n", e.getMessage());
            return STATUS_IO_ERROR;
        }

        Sampler sampler = config.getSamplerConfig().newSampler(featureModel);
        Composer composer = config.getComposerConfig().newComposer();
        Analyzer analyzer;
        try {
            analyzer = config.getAnalyzerConfig().newAnalyzer(analyzerTmpDirectory);
        } catch (IOException e) {
            System.err.println("Failed to instantiate composer:");
            e.printStackTrace();
            return STATUS_IO_ERROR;
        }

        List<AnalysisResult> allAnalysisResults = new ArrayList<>();
        List<AnalysisResult> iterationAnalysisResults = List.of();
        for (int i = 0; i < config.getIterations(); i++) {
            List<List<String>> sample;
            try {
                sample = sampler.sample(iterationAnalysisResults);
            } catch (SamplerException e) {
                System.err.println("A sampler error occurred");
                e.printStackTrace();
                return STATUS_INTERNAL_ERROR;
            }
            iterationAnalysisResults = new ArrayList<>();
            for (int j = 0; j < sample.size(); j++) {
                List<String> features = sample.get(j);
                System.out.println("Analyzing variant with features " + features);
                String destinationDirectoryName = String.format("%d-%d", i, j);
                Path composerDestination = tmpDir.resolve(destinationDirectoryName);
                try {
                    Files.createDirectories(composerDestination);
                } catch (IOException e) {
                    System.err.printf("Failed to create composer destination directory: %s%n", e.getMessage());
                    return STATUS_IO_ERROR;
                }

                CompositionInformation composedSourceLocation;
                try {
                    composedSourceLocation = composer.compose(features, composerDestination);
                } catch (IllegalFeatureNameException e) {
                    System.err.println("Invalid feature name has been found");
                    return STATUS_INVALID_CONFIG;
                } catch (IOException e) {
                    System.err.println("An IO error occurred:");
                    e.printStackTrace();
                    return STATUS_IO_ERROR;
                } catch (ComposerException e) {
                    System.err.println("A composer error occurred:");
                    e.printStackTrace();
                    return STATUS_INTERNAL_ERROR;
                }

                try {
                    iterationAnalysisResults.add(analyzer.analyze(composedSourceLocation));
                } catch (IOException e) {
                    System.err.println("An I/O error occurred while running the analyzer");
                    e.printStackTrace();
                    return STATUS_IO_ERROR;
                } catch (AnalyzerFailureException e) {
                    System.err.println("The analysis did not complete successfully.");
                    e.printStackTrace();
                    return STATUS_INTERNAL_ERROR;
                }
            }
            allAnalysisResults.addAll(iterationAnalysisResults);
        }

        printSummary(allAnalysisResults);
        return 0;
    }

    private static void printSummary(List<AnalysisResult> analysisResults) {
        System.out.println("Summary:");
        for (AnalysisResult analysisResult : analysisResults) {
            System.out.println(analysisResult);
        }
    }
}