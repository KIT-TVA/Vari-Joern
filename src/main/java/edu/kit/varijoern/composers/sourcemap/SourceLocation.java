package edu.kit.varijoern.composers.sourcemap;

import java.nio.file.Path;

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
}
