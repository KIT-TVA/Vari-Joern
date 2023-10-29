package edu.kit.varijoern.featuremodel;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import jodd.util.ResourcesUtil;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads a feature model from Kconfig files using <a href="https://github.com/ekuiter/torte/tree/main">torte</a> and
 * <a href="https://github.com/paulgazz/kmax">kmax</a>. So far, only the Linux kernel is supported.
 */
public class TorteKmaxFMReader implements FeatureModelReader {
    public static final String NAME = "torte-kmax";
    private final Path sourcePath;

    /**
     * Creates a new {@link TorteKmaxFMReader} that reads the feature model from the Kconfig files located in the
     * specified source directory.
     *
     * @param sourcePath the path to the source directory
     */
    public TorteKmaxFMReader(Path sourcePath) {
        this.sourcePath = sourcePath;
    }

    @Override
    public IFeatureModel read(Path tmpPath) throws IOException, FeatureModelReaderException {
        String readerScript = ResourcesUtil.getResourceAsString("torte/linux-working-tree-kmax.sh");
        Path readerScriptPath = tmpPath.resolve("linux-working-tree-kmax.sh");
        Files.writeString(readerScriptPath, readerScript, StandardCharsets.UTF_8);

        try {
            Files.createDirectories(tmpPath.resolve("input"));
            FileUtils.copyDirectory(this.sourcePath.toFile(), tmpPath.resolve("input/linux").toFile());

            runReader(tmpPath);

            Path fmPath = tmpPath.resolve("output/model_to_xml_featureide/linux/vari-joern-auto-tag[x86].xml");
            return new FeatureIDEFMReader(fmPath).read(tmpPath);
        } finally {
            // The source code probably takes a large amount of disk space.
            FileUtils.deleteDirectory(tmpPath.resolve("input").toFile());
        }
    }

    private void runReader(Path tmpPath) throws IOException, FeatureModelReaderException {
        ProcessBuilder readerProcessBuilder = new ProcessBuilder("bash", "linux-working-tree-kmax.sh")
            .inheritIO()
            .directory(tmpPath.toFile());
        readerProcessBuilder.environment().put("TORTE_INPUT_DIRECTORY", tmpPath.resolve("input").toString());
        readerProcessBuilder.environment().put("TORTE_OUTPUT_DIRECTORY", tmpPath.resolve("output").toString());
        Process readerProcess = readerProcessBuilder.start();
        int readerExitCode;
        try {
            readerExitCode = readerProcess.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException("Unexpected interruption of Torte process", e);
        }
        if (readerExitCode != 0) {
            throw new FeatureModelReaderException("Torte exited with non-zero exit code: " + readerExitCode);
        }
    }
}
