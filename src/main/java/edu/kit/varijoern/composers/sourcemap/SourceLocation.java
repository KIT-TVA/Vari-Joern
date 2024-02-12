package edu.kit.varijoern.composers.sourcemap;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents a location in a source file.
 *
 * @param file the path to the file
 * @param line the line number, 1-based
 */
public record SourceLocation(Path file, int line) {
    @Override
    public String toString() {
        return file + ":" + line;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SourceLocation that = (SourceLocation) o;
        return line == that.line && Objects.equals(file, that.file);
    }

    @Override
    public int hashCode() {
        return Objects.hash(file, line);
    }
}
