package edu.kit.varijoern.composers.kbuild;

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
    public InclusionInformation(Path filePath, Set<String> includedFiles, Map<String, String> defines, List<String> includePaths) {
        this.filePath = filePath.normalize();
        this.includedFiles = includedFiles;
        this.defines = defines;
        this.includePaths = includePaths;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InclusionInformation that = (InclusionInformation) o;
        return Objects.equals(filePath, that.filePath) && Objects.equals(includedFiles, that.includedFiles) && Objects.equals(defines, that.defines);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filePath, includedFiles, defines);
    }
}
