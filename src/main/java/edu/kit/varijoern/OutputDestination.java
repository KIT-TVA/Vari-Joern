package edu.kit.varijoern;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Describes the destination of the output of the program. This is either a file or the standard output.
 */
public class OutputDestination {
    @Nullable
    final Path path;

    /**
     * Creates a new output destination that represents the standard output.
     */
    public OutputDestination() {
        this.path = null;
    }

    /**
     * Creates a new output destination that represents a file.
     *
     * @param path the path to the file
     */
    public OutputDestination(@Nullable Path path) {
        this.path = path;
    }

    /**
     * Creates a new output destination by parsing the specified path. If the path is "-", the standard output is used.
     * This is intended to be used for parsing command line arguments.
     *
     * @param path the path to the file or "-"
     */
    public OutputDestination(@NotNull String path) {
        if (path.equals("-")) {
            this.path = null;
        } else {
            this.path = Path.of(path);
        }
    }

    /**
     * Returns the path to the file if the output is written to a file.
     *
     * @return the path to the file
     */
    public Optional<Path> getPath() {
        return Optional.ofNullable(path);
    }

    /**
     * Returns the stream to which the output should be written. If the output is written to a file, the file and its
     * parent directories are created if it does not exist.
     *
     * @return the stream to which the output should be written
     * @throws IOException       if an I/O error occurs
     * @throws SecurityException if a security manager exists, and it denies the permission to create the file or
     *                           one of the parent directories
     */
    public PrintStream getStream() throws IOException {
        if (path == null) {
            return System.out;
        }

        Path parentDirectory = path.getParent();
        if (parentDirectory != null) {
            Files.createDirectories(parentDirectory);
        }

        return new PrintStream(path.toFile());
    }
}
