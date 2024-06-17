package edu.kit.varijoern.featuremodel;

import de.ovgu.featureide.fm.core.base.IConstraint;
import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.KconfigTestCaseManager;
import edu.kit.varijoern.featuremodel.tortekmax.TorteKmaxFMReader;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TorteKmaxFMReaderTest {
    @Test
    void buildFeatureModelBusyBox() throws GitAPIException, IOException, FeatureModelReaderException, InterruptedException {
        runTestFor("busybox-sample", "busybox");
    }

    @Test
    void buildFeatureModelLinux() throws GitAPIException, IOException, FeatureModelReaderException, InterruptedException {
        runTestFor("linux-sample", "linux");
    }

    private void runTestFor(String testCase, String system)
            throws GitAPIException, IOException, FeatureModelReaderException, InterruptedException {
        KconfigTestCaseManager testCaseManager = new KconfigTestCaseManager(testCase);
        IFeatureModel featureModel = buildFeatureModel(testCaseManager, system);
        assertEquals(List.of(), testCaseManager.getModifications(), "Feature model reader modified original source");
        assertEquals(testCaseManager.getCorrectFeatureModel().getFeatures().stream()
                        .map(IFeature::getName)
                        .collect(Collectors.toSet()),
                featureModel.getFeatures().stream()
                        .map(IFeature::getName)
                        .collect(Collectors.toSet())
        );
        assertEquals(testCaseManager.getCorrectFeatureModel().getConstraints().stream()
                        .map(IConstraint::getNode)
                        .collect(Collectors.toSet()),
                featureModel.getConstraints().stream()
                        .map(IConstraint::getNode)
                        .collect(Collectors.toSet())
        );
    }

    private IFeatureModel buildFeatureModel(KconfigTestCaseManager testCaseManager, String system)
            throws IOException, FeatureModelReaderException, InterruptedException {
        TorteKmaxFMReader fmReader = new TorteKmaxFMReader(testCaseManager.getPath(), system);
        return fmReader.read(Files.createTempDirectory("vari-joern-test-fm-reader"));
    }
}
