package edu.kit.varijoern;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.impl.CoreFactoryWorkspaceLoader;
import de.ovgu.featureide.fm.core.base.impl.DefaultFeatureModelFactory;
import de.ovgu.featureide.fm.core.base.impl.FMFactoryManager;
import de.ovgu.featureide.fm.core.base.impl.FMFormatManager;
import de.ovgu.featureide.fm.core.io.manager.FeatureModelManager;
import de.ovgu.featureide.fm.core.io.xml.XmlFeatureModelFormat;
import edu.kit.varijoern.composers.Composer;
import edu.kit.varijoern.composers.ComposerException;
import edu.kit.varijoern.config.Config;
import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.samplers.Sampler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Main {
    private static final String USAGE = "Usage: vari-joern [config]";
    private static final int STATUS_COMMAND_LINE_USAGE_ERROR = 64;
    private static final int STATUS_INVALID_CONFIG = 78;
    private static final int STATUS_IO_ERROR = 74;

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

        Sampler sampler = config.getSamplerConfig().newSampler(featureModel);
        Composer composer = config.getComposerConfig().newComposer(featureModel);
        Path tmpDir;
        try {
            tmpDir = Files.createTempDirectory("vari-joern");
        } catch (IOException e) {
            System.err.printf("Failed to create temporary directory: %s%n", e.getMessage());
            return STATUS_IO_ERROR;
        }

        for (int i = 0; i < config.getIterations(); i++) {
            List<List<String>> sample = sampler.sample();
            for (int j = 0; j < sample.size(); j++) {
                String destinationDirectoryName = String.format("%d-%d", i, j);
                Path composerDestination = tmpDir.resolve(destinationDirectoryName);
                try {
                    Files.createDirectories(composerDestination);
                } catch (IOException e) {
                    System.err.printf("Failed to create composer destination directory: %s%n", e.getMessage());
                    return STATUS_IO_ERROR;
                }
                List<String> features = sample.get(j);

                try {
                    composer.compose(features, composerDestination);
                } catch (IllegalFeatureNameException e) {
                    System.err.println("Invalid feature name has been found");
                    return STATUS_INVALID_CONFIG;
                } catch (IOException e) {
                    System.err.printf("An IO error occurred: %s%n", e.getMessage());
                    return STATUS_IO_ERROR;
                } catch (ComposerException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return 0;
    }
}