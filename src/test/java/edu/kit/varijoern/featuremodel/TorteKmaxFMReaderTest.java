package edu.kit.varijoern.featuremodel;

import de.ovgu.featureide.fm.core.base.IConstraint;
import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.KconfigTestCaseManager;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TorteKmaxFMReaderTest {
    @Test
    void buildFeatureModelBusyBox() throws GitAPIException, IOException, FeatureModelReaderException {
        runTestFor("busybox-sample", "busybox");
    }

    private void runTestFor(String testCase, String system)
            throws GitAPIException, IOException, FeatureModelReaderException {
        KconfigTestCaseManager testCaseManager = new KconfigTestCaseManager(testCase);
        IFeatureModel featureModel = buildFeatureModel(testCaseManager, system);
        assertEquals(List.of(), testCaseManager.getModifications(), "Feature model reader modified original source");
        assertEquals(featureModel.getFeatures().stream()
                        .map(IFeature::getName)
                        .collect(Collectors.toSet()),
                testCaseManager.getCorrectFeatureModel().getFeatures().stream()
                        .map(IFeature::getName)
                        .collect(Collectors.toSet())
        );
        assertEquals(featureModel.getConstraints().stream()
                        .map(IConstraint::getNode)
                        .collect(Collectors.toSet()),
                testCaseManager.getCorrectFeatureModel().getConstraints().stream()
                        .map(IConstraint::getNode)
                        .collect(Collectors.toSet())
        );
    }

    private IFeatureModel buildFeatureModel(KconfigTestCaseManager testCaseManager, String system)
            throws IOException, FeatureModelReaderException {
        TorteKmaxFMReader fmReader = new TorteKmaxFMReader(testCaseManager.getPath(), system);
        return fmReader.read(Files.createTempDirectory("vari-joern-test-fm-reader"));
    }
}
