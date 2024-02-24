package edu.kit.varijoern;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import edu.kit.varijoern.featuremodel.FeatureIDEFMReader;
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
import java.util.List;
import java.util.stream.Stream;

/**
 * A helper class for preparing test cases for the Kconfig/Kbuild implementation.
 * If a test case is named {@code foo}, the sources are expected to be located in
 * {@code src/test/resources/kconfigtestcases/sources/foo} and the correct feature model in
 * {@code src/test/resources/kconfigtestcases/models/foo.xml}.
 */
public class KconfigTestCaseManager {
    private final Path pathToExtractedTestCase;
    private final Git git;
    private final RevCommit initialCommit;
    private final IFeatureModel correctFeatureModel;

    public KconfigTestCaseManager(@NotNull String testCaseName) throws IOException, GitAPIException {
        this(testCaseName, (path) -> {
        });
    }

    public KconfigTestCaseManager(@NotNull String testCaseName, @NotNull KconfigTestCasePreparer preparer)
            throws IOException, GitAPIException {
        ClassLoader classLoader = this.getClass().getClassLoader();
        URL resourceLocation = classLoader.getResource("kconfigtestcases/sources/%s".formatted(testCaseName));
        URL correctFeatureModelLocation = classLoader.getResource(
                "kconfigtestcases/models/%s.xml".formatted(testCaseName)
        );
        if (resourceLocation == null) {
            throw new IllegalArgumentException("Test case sources not found: " + testCaseName);
        }
        if (correctFeatureModelLocation == null) {
            throw new IllegalArgumentException("Test case feature model not found: " + testCaseName);
        }

        this.pathToExtractedTestCase = Files.createTempDirectory("vari-joern-test-case");

        System.err.printf("Extracting test case to %s%n", this.pathToExtractedTestCase);

        this.correctFeatureModel = new FeatureIDEFMReader(Path.of(correctFeatureModelLocation.getPath()))
                .read(this.pathToExtractedTestCase);
        // In case the FM reader wrote something to the directory
        FileUtils.cleanDirectory(this.pathToExtractedTestCase.toFile());

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
}
