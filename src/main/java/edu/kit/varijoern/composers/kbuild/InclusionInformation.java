package edu.kit.varijoern.composers.kbuild;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Contains information about how a source file is compiled.
 *
 * @param filePath           the path to the source file, relative to the source directory
 * @param includedFiles      the files included by specifying them as compiler arguments
 * @param defines            the additional defines specified as compiler arguments
 * @param includePaths       the additional include directories specified as compiler arguments
 * @param systemIncludePaths the additional system include directories specified as compiler arguments
 */
public record InclusionInformation(Path filePath, Set<Path> includedFiles, Map<String, String> defines,
                                   List<Path> includePaths, List<Path> systemIncludePaths) {
    public InclusionInformation(@NotNull Path filePath, Set<Path> includedFiles, Map<String, String> defines,
                                List<Path> includePaths, List<Path> systemIncludePaths) {
        this.filePath = filePath.normalize();
        this.includedFiles = includedFiles;
        this.defines = defines;
        this.includePaths = includePaths;
        this.systemIncludePaths = systemIncludePaths;
    }

    /**
     * Creates a list of {@link InclusionInformation} objects from a {@link GCCCall}.
     *
     * @param call            the GCC call to extract the information from
     * @param buildDirectory  the directory where the build is executed. The paths in the GCC call are relative to this
     *                        directory. For example, fiasco by default uses the `build` directory as the output
     *                        directory and executes `make` within it.
     * @param sourceDirectory the root directory of the source files. The paths in the created objects will be relative
     *                        to this directory if they are within it.
     * @return a list of {@link InclusionInformation} objects
     */
    public static List<InclusionInformation> fromGCCCall(GCCCall call, Path buildDirectory, Path sourceDirectory) {
        Stream<Path> filePaths = call.compiledFiles().stream()
                .map(filePath -> normalizePath(Path.of(filePath), buildDirectory, sourceDirectory));
        List<Path> includedFiles = call.includes().stream()
                .map(filePath -> normalizePath(Path.of(filePath), buildDirectory, sourceDirectory))
                .toList();
        Map<String, String> defines = call.defines();
        List<Path> includePaths = call.includePaths().stream()
                .map(path -> normalizePath(Path.of(path), buildDirectory, sourceDirectory))
                .toList();
        List<Path> systemIncludePaths = call.systemIncludePaths().stream()
                .map(path -> normalizePath(Path.of(path), buildDirectory, sourceDirectory))
                .toList();
        return filePaths.map(filePath -> new InclusionInformation(filePath, Set.copyOf(includedFiles), defines,
                        includePaths, systemIncludePaths))
                .toList();
    }

    private static Path normalizePath(@NotNull Path path, @NotNull Path buildDirectory, @NotNull Path sourceDirectory) {
        Path absolutePath = (path.isAbsolute() ? path : buildDirectory.resolve(path))
                .normalize();
        if (absolutePath.startsWith(sourceDirectory))
            return sourceDirectory.relativize(absolutePath).normalize();
        return absolutePath;
    }

    /**
     * Returns the relative path to the file that the {@link KbuildComposer} would use for the composed file.
     *
     * @return the path, relative to the composer's output directory
     */
    @NotNull
    public Path getComposedFilePath() {
        String fileName = this.filePath.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = dotIndex == -1 ? fileName : fileName.substring(0, dotIndex);
        String extension = dotIndex == -1 ? "" : fileName.substring(dotIndex);
        return this.filePath.getParent()
                .resolve(baseName + "-" + this.getComposedFileNameHash() + extension);
    }

    private HashCode getComposedFileNameHash() {
        Hasher hasher = Hashing.sha256().newHasher();
        hasher.putString(this.filePath.toString(), StandardCharsets.UTF_8);
        hasher.putByte((byte) 0);
        for (String includedFile : this.includedFiles.stream().map(Path::toString).sorted().toList()) {
            hasher.putString(includedFile, StandardCharsets.UTF_8);
            hasher.putByte((byte) 0);
        }
        hasher.putByte((byte) 0);
        for (Map.Entry<String, String> define : this.defines.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .toList()) {
            hasher.putString(define.getKey(), StandardCharsets.UTF_8);
            hasher.putByte((byte) 0);
            hasher.putString(define.getValue(), StandardCharsets.UTF_8);
            hasher.putByte((byte) 0);
        }
        return hasher.hash();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InclusionInformation that = (InclusionInformation) o;
        return Objects.equals(filePath, that.filePath)
                && Objects.equals(includedFiles, that.includedFiles)
                && Objects.equals(includePaths, that.includePaths)
                && Objects.equals(defines, that.defines)
                && Objects.equals(systemIncludePaths, that.systemIncludePaths);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filePath, includedFiles, defines);
    }
}
