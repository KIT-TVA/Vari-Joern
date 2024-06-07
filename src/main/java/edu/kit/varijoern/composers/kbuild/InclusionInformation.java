package edu.kit.varijoern.composers.kbuild;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Contains information about how a source file is compiled.
 *
 * @param filePath      the path to the source file, relative to the source directory
 * @param includedFiles the files included by specifying them as compiler arguments
 * @param defines       the additional defines specified as compiler arguments
 * @param includePaths  the additional include directories specified as compiler arguments
 */
public record InclusionInformation(Path filePath, Set<String> includedFiles, Map<String, String> defines,
                                   List<String> includePaths) {
    public InclusionInformation(@NotNull Path filePath, Set<String> includedFiles, Map<String, String> defines,
                                List<String> includePaths) {
        this.filePath = filePath.normalize();
        this.includedFiles = includedFiles;
        this.defines = defines;
        this.includePaths = includePaths;
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
                .resolve(baseName + "-" + this.hashCode() + extension);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InclusionInformation that = (InclusionInformation) o;
        return Objects.equals(filePath, that.filePath)
                && Objects.equals(includedFiles, that.includedFiles)
                && Objects.equals(defines, that.defines);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filePath, includedFiles, defines);
    }
}
