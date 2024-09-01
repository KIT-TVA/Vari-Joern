package edu.kit.varijoern;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.featuremodel.featureide.FeatureIDEFMReader;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A helper class for preparing test cases for the Kconfig/Kbuild implementation.
 * If a test case is named {@code foo}, the sources are expected to be located in
 * {@code src/test/resources/kconfigtestcases/foo/sources}, the correct feature model in
 * {@code src/test/resources/kconfigtestcases/foo/model.xml}, and the presence conditions in
 * {@code src/test/resources/kconfigtestcases/foo/presence-conditions.txt}.
 */
public class KconfigTestCaseManager {
    private final Path pathToExtractedTestCase;
    private final Git git;
    private final RevCommit initialCommit;
    private final KconfigTestCaseMetadata metadata;
    private final IFeatureModel correctFeatureModel;
    private final Map<Path, List<PresenceConditionExpectation>> presenceConditionExpectations;

    public KconfigTestCaseManager(@NotNull String testCaseName) throws IOException, GitAPIException {
        this(testCaseName, (path) -> {
        });
    }

    public KconfigTestCaseManager(@NotNull String testCaseName, @NotNull KconfigTestCasePreparer preparer)
            throws IOException, GitAPIException {
        ClassLoader classLoader = this.getClass().getClassLoader();
        URL resourceLocation = classLoader.getResource("kconfigtestcases/%s/source".formatted(testCaseName));
        URL correctFeatureModelLocation = classLoader.getResource(
                "kconfigtestcases/%s/model.xml".formatted(testCaseName)
        );
        if (resourceLocation == null) {
            throw new InvalidTestCaseException("Test case sources not found: " + testCaseName);
        }
        if (correctFeatureModelLocation == null) {
            throw new InvalidTestCaseException("Test case feature model not found: " + testCaseName);
        }

        this.pathToExtractedTestCase = Files.createTempDirectory("vari-joern-test-case");

        System.err.printf("Extracting test case to %s%n", this.pathToExtractedTestCase);

        this.correctFeatureModel = new FeatureIDEFMReader(Path.of(correctFeatureModelLocation.getPath()))
                .read(this.pathToExtractedTestCase);
        // In case the FM reader wrote something to the directory
        FileUtils.cleanDirectory(this.pathToExtractedTestCase.toFile());

        this.metadata = readMetadata(testCaseName);
        this.presenceConditionExpectations = readPresenceConditionExpectations(testCaseName);

        try (Stream<Path> originalPaths = Files.walk(Path.of(resourceLocation.getPath()))) {
            originalPaths.forEach(sourcePath -> {
                try {
                    Path targetPath = this.pathToExtractedTestCase.resolve(Path.of(resourceLocation.getPath())
                            .relativize(sourcePath));
                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.copy(sourcePath, targetPath);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        preparer.prepareTestCase(this.pathToExtractedTestCase);

        Repository repo = FileRepositoryBuilder.create(pathToExtractedTestCase.resolve(".git").toFile());
        repo.create();
        this.git = new Git(repo);
        this.git.add().addFilepattern(".").call();
        this.initialCommit = this.git.commit().setMessage("Initial commit").call();
    }

    private KconfigTestCaseMetadata readMetadata(@NotNull String testCaseName) {
        URL metadataLocation = this.getClass().getClassLoader().getResource(
                "kconfigtestcases/%s/metadata.json".formatted(testCaseName)
        );
        if (metadataLocation == null)
            throw new InvalidTestCaseException("Test case metadata not found: " + testCaseName);
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(metadataLocation, KconfigTestCaseMetadata.class);
        } catch (IOException e) {
            throw new InvalidTestCaseException("Test case metadata could not be read.", e);
        }
    }

    private Map<Path, List<PresenceConditionExpectation>> readPresenceConditionExpectations(
            @NotNull String testCaseName)
            throws IOException {
        URL presenceConditionExpectationsLocation = this.getClass().getClassLoader().getResource(
                "kconfigtestcases/%s/presence-conditions.txt".formatted(testCaseName)
        );
        if (presenceConditionExpectationsLocation == null)
            throw new InvalidTestCaseException("Test case presence conditions not found: " + testCaseName);
        Map<Path, List<PresenceConditionExpectation>> result;
        try {
            result = PresenceConditionExpectationParser.parse(
                    Files.readString(Path.of(presenceConditionExpectationsLocation.getPath())));
        } catch (ParseException e) {
            throw new InvalidTestCaseException("Presence conditions could not be parsed.", e);
        }
        return result;
    }

    public Path getPath() {
        return this.pathToExtractedTestCase;
    }

    public IFeatureModel getCorrectFeatureModel() {
        return this.correctFeatureModel;
    }

    public List<DiffEntry> getModifications() throws GitAPIException, IOException {
        CanonicalTreeParser initialCommitIterator = new CanonicalTreeParser();
        initialCommitIterator.reset(this.git.getRepository().newObjectReader(), this.initialCommit.getTree());

        return this.git.diff()
                .setOldTree(initialCommitIterator)
                .setNewTree(new FileTreeIterator(this.git.getRepository()))
                .call();
    }

    public Optional<List<PresenceConditionExpectation>> getPresenceConditionExpectations(Path relativePath) {
        return Optional.ofNullable(presenceConditionExpectations.get(relativePath));
    }

    public KconfigTestCaseMetadata getMetadata() {
        return this.metadata;
    }
}
