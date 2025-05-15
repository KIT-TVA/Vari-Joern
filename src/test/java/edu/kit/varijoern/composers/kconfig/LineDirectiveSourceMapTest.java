package edu.kit.varijoern.composers.kconfig;

import edu.kit.varijoern.composers.sourcemap.SourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LineDirectiveSourceMapTest {
    private @TempDir Path tmpDir;

    private Path prepareFile(String content) throws IOException {
        Path file = Files.createTempFile(this.tmpDir, "test", ".c");
        Files.writeString(file, content);
        return file;
    }

    @Test
    void emptyFile() throws IOException {
        Path file = prepareFile("");
        LineDirectiveSourceMap sourceMap = new LineDirectiveSourceMap(file);
        assertEquals(Optional.of(new SourceLocation(file, 1)), sourceMap.getOriginalLocation(1));
    }

    @Test
    void emptyFileWithLineDirective() throws IOException {
        Path file = prepareFile("#line 42 \"foo.c\"\n");
        LineDirectiveSourceMap sourceMap = new LineDirectiveSourceMap(file);
        assertEquals(Optional.of(new SourceLocation(file, 1)), sourceMap.getOriginalLocation(1));
        assertEquals(Optional.of(new SourceLocation(Path.of("foo.c"), 42)),
                sourceMap.getOriginalLocation(2));
    }

    @Test
    void mapAfterFileEnd() throws IOException {
        Path file = prepareFile("#line 42 \"foo.c\"\n");
        LineDirectiveSourceMap sourceMap = new LineDirectiveSourceMap(file);
        assertEquals(Optional.of(new SourceLocation(file, 1)), sourceMap.getOriginalLocation(1));
        assertEquals(Optional.of(new SourceLocation(Path.of("foo.c"), 42)),
                sourceMap.getOriginalLocation(2));
        assertEquals(Optional.of(new SourceLocation(Path.of("foo.c"), 43)),
                sourceMap.getOriginalLocation(3));
    }

    @Test
    void multipleLineDirectives() throws IOException {
        Path file = prepareFile("""
                #line 42 "foo.c"
                int main() {
                #line 1 "bar.c"
                  return 0;
                }
                """);
        LineDirectiveSourceMap sourceMap = new LineDirectiveSourceMap(file);
        assertEquals(Optional.of(new SourceLocation(file, 1)), sourceMap.getOriginalLocation(1));
        assertEquals(Optional.of(new SourceLocation(Path.of("foo.c"), 42)),
                sourceMap.getOriginalLocation(2));
        assertEquals(Optional.of(new SourceLocation(Path.of("foo.c"), 43)),
                sourceMap.getOriginalLocation(3));
        assertEquals(Optional.of(new SourceLocation(Path.of("bar.c"), 1)),
                sourceMap.getOriginalLocation(4));
        assertEquals(Optional.of(new SourceLocation(Path.of("bar.c"), 2)),
                sourceMap.getOriginalLocation(5));
        assertEquals(Optional.of(new SourceLocation(Path.of("bar.c"), 3)),
                sourceMap.getOriginalLocation(6));
    }

    @Test
    void noFilename() throws IOException {
        Path file = prepareFile("#line 42\n");
        LineDirectiveSourceMap sourceMap = new LineDirectiveSourceMap(file);
        assertEquals(Optional.of(new SourceLocation(file, 1)), sourceMap.getOriginalLocation(1));
        assertEquals(Optional.of(new SourceLocation(file, 42)),
                sourceMap.getOriginalLocation(2));
    }

    @Test
    void mixFilenameAndNoFilename() throws IOException {
        Path file = prepareFile("""
                #line 42 "foo.c"
                #line 1
                """);
        LineDirectiveSourceMap sourceMap = new LineDirectiveSourceMap(file);
        assertEquals(Optional.of(new SourceLocation(file, 1)), sourceMap.getOriginalLocation(1));
        assertEquals(Optional.of(new SourceLocation(Path.of("foo.c"), 42)),
                sourceMap.getOriginalLocation(2));
        assertEquals(Optional.of(new SourceLocation(Path.of("foo.c"), 1)),
                sourceMap.getOriginalLocation(3));
    }

    @Test
    void whitespaceBeforeHash() throws IOException {
        Path file = prepareFile(" #line 42 \"foo.c\"\n");
        LineDirectiveSourceMap sourceMap = new LineDirectiveSourceMap(file);
        assertEquals(Optional.of(new SourceLocation(file, 1)), sourceMap.getOriginalLocation(1));
        assertEquals(Optional.of(new SourceLocation(Path.of("foo.c"), 42)),
                sourceMap.getOriginalLocation(2));
    }

    @Test
    void whitespaceAfterHash() throws IOException {
        Path file = prepareFile("# line 42 \"foo.c\"\n");
        LineDirectiveSourceMap sourceMap = new LineDirectiveSourceMap(file);
        assertEquals(Optional.of(new SourceLocation(file, 1)), sourceMap.getOriginalLocation(1));
        assertEquals(Optional.of(new SourceLocation(Path.of("foo.c"), 42)),
                sourceMap.getOriginalLocation(2));
    }

    @Test
    void multipleWhitespacesAfterLine() throws IOException {
        Path file = prepareFile("#line  42 \"foo.c\"\n");
        LineDirectiveSourceMap sourceMap = new LineDirectiveSourceMap(file);
        assertEquals(Optional.of(new SourceLocation(file, 1)), sourceMap.getOriginalLocation(1));
        assertEquals(Optional.of(new SourceLocation(Path.of("foo.c"), 42)),
                sourceMap.getOriginalLocation(2));
    }

    @Test
    void multipleWhitespacesAfterLineNumber() throws IOException {
        Path file = prepareFile("#line 42  \"foo.c\"\n");
        LineDirectiveSourceMap sourceMap = new LineDirectiveSourceMap(file);
        assertEquals(Optional.of(new SourceLocation(file, 1)), sourceMap.getOriginalLocation(1));
        assertEquals(Optional.of(new SourceLocation(Path.of("foo.c"), 42)),
                sourceMap.getOriginalLocation(2));
    }

    @Test
    void backslashBeforeLineDirective() throws IOException {
        Path file = prepareFile("""
                random line
                \\
                #line 42 "foo.c"
                """);
        LineDirectiveSourceMap sourceMap = new LineDirectiveSourceMap(file);
        assertEquals(Optional.of(new SourceLocation(file, 1)), sourceMap.getOriginalLocation(1));
        assertEquals(Optional.of(new SourceLocation(file, 2)), sourceMap.getOriginalLocation(2));
        assertEquals(Optional.of(new SourceLocation(file, 3)), sourceMap.getOriginalLocation(3));
        assertEquals(Optional.of(new SourceLocation(Path.of("foo.c"), 42)),
                sourceMap.getOriginalLocation(4));
    }

    @Test
    void invalidatingBackslashBeforeLineDirective() throws IOException {
        Path file = prepareFile("""
                random line\\
                #line 42 "foo.c"
                """);
        LineDirectiveSourceMap sourceMap = new LineDirectiveSourceMap(file);
        assertEquals(Optional.of(new SourceLocation(file, 1)), sourceMap.getOriginalLocation(1));
        assertEquals(Optional.of(new SourceLocation(file, 2)), sourceMap.getOriginalLocation(2));
        assertEquals(Optional.of(new SourceLocation(file, 3)), sourceMap.getOriginalLocation(3));
    }

    @Test
    void directiveSplitAcrossLines() throws IOException {
        Path file = prepareFile("""
                #line 42 \\
                "foo.c"
                """);
        LineDirectiveSourceMap sourceMap = new LineDirectiveSourceMap(file);
        assertEquals(Optional.of(new SourceLocation(file, 1)), sourceMap.getOriginalLocation(1));
        assertEquals(Optional.of(new SourceLocation(file, 2)), sourceMap.getOriginalLocation(2));
        assertEquals(Optional.of(new SourceLocation(Path.of("foo.c"), 42)),
                sourceMap.getOriginalLocation(3));
    }

    @Test
    void multipleBackslashesInOneLine() throws IOException {
        Path file = prepareFile("""
                \\ random line \\
                #line 42
                """);
        LineDirectiveSourceMap sourceMap = new LineDirectiveSourceMap(file);
        assertEquals(Optional.of(new SourceLocation(file, 1)), sourceMap.getOriginalLocation(1));
        assertEquals(Optional.of(new SourceLocation(file, 2)), sourceMap.getOriginalLocation(2));
        assertEquals(Optional.of(new SourceLocation(file, 3)), sourceMap.getOriginalLocation(3));
    }
}