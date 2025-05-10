package edu.kit.varijoern.composers.kbuild;

import edu.kit.varijoern.composers.sourcemap.SourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * A source map that maps line numbers in a file to the original source location by interpreting #line directives.
 */
public class LineDirectiveSourceMap {
    private static final Pattern LINE_DIRECTIVE_PATTERN
            = Pattern.compile("\\s*#\\s*line\\s+(\\d+)(?:\\s+\"([^\"]+)\")?");
    private final SortedMap<Integer, SourceLocation> locationBeginnings = new TreeMap<>();

    /**
     * Creates a new LineDirectiveSourceMap for the given file, reading its #line directives.
     *
     * @param path the path to the file
     * @throws IOException if an I/O error occurs while reading the file
     */
    public LineDirectiveSourceMap(Path path) throws IOException {
        locationBeginnings.put(1, new SourceLocation(path, 1));

        List<String> lines = Files.readAllLines(path);
        for (int i = 0; i < lines.size() - 1; i++) {
            String line = lines.get(i);
            if (line.trim().endsWith("\\")) {
                lines.set(i + 1, line.substring(0, line.lastIndexOf('\\')) + lines.get(i + 1));
                lines.set(i, "");
            }
        }

        Path currentSourcePath = path;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            var matcher = LINE_DIRECTIVE_PATTERN.matcher(line);
            if (matcher.matches()) {
                int lineNumber = Integer.parseInt(matcher.group(1));
                String sourcePath = matcher.group(2);
                if (sourcePath != null) {
                    currentSourcePath = Path.of(sourcePath);
                }
                locationBeginnings.put(i + 2, new SourceLocation(currentSourcePath, lineNumber));
            }
        }
    }

    /**
     * Returns the original source location of the given line number, as specified by the #line directives.
     *
     * @param line the line number
     * @return the original source location, or an empty Optional if the line number is out of bounds for the file
     */
    public Optional<SourceLocation> getOriginalLocation(int line) {
        SortedMap<Integer, SourceLocation> locationSection = locationBeginnings.headMap(line + 1);
        if (locationSection.isEmpty()) {
            return Optional.empty();
        }
        Integer locationAnnotationBeginning = locationSection.lastKey();
        SourceLocation originalLocationOfAnnotationBeginning = locationBeginnings.get(locationAnnotationBeginning);
        return Optional.of(new SourceLocation(
                originalLocationOfAnnotationBeginning.file(),
                originalLocationOfAnnotationBeginning.line() + line - locationAnnotationBeginning
        ));
    }
}
