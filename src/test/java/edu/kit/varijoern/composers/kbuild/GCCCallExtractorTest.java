package edu.kit.varijoern.composers.kbuild;

import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GCCCallExtractorTest {
    @Test
    void emptyString() throws ParseException {
        assertEquals(new GCCCallExtractor("").getCalls(), List.of());
    }

    @Test
    void noArguments() throws ParseException {
        assertEquals(List.of(new GCCCall(List.of(), List.of(), List.of(), Map.of())),
            new GCCCallExtractor("gcc").getCalls()
        );
    }

    @Test
    void singleFile() throws ParseException {
        assertEquals(List.of(new GCCCall(List.of("code.c"), List.of(), List.of(), Map.of())),
            new GCCCallExtractor("gcc code.c").getCalls()
        );
    }

    @Test
    void multipleFiles() throws ParseException {
        assertEquals(List.of(new GCCCall(List.of("code.c", "code2.c"), List.of(), List.of(), Map.of())),
            new GCCCallExtractor("gcc code.c code2.c").getCalls()
        );
    }

    @Test
    void allArgumentTypes() throws ParseException {
        assertEquals(List.of(new GCCCall(
                List.of("code.c"),
                List.of("./my inc folder", "/usr/include"),
                List.of("boot.h"),
                Map.of("FOO", "abc", "__KERNEL__", "")
            )),
            new GCCCallExtractor(
                "gcc -I\"./my inc folder\" -O2 -I/usr/include -DFOO=abc -D__KERNEL__ -include boot.h code.c"
            ).getCalls()
        );
    }

    @Test
    void includePath() throws ParseException {
        assertEquals(List.of(new GCCCall(
                List.of(),
                List.of("abc/def", "ghi/jkl"),
                List.of(),
                Map.of()
            )),
            new GCCCallExtractor(
                "gcc -Iabc/def -I ghi/jkl"
            ).getCalls()
        );
    }

    @Test
    void includeFile() throws ParseException {
        assertEquals(List.of(new GCCCall(
                List.of(),
                List.of(),
                List.of("my/great file.h", "another/file.h"),
                Map.of()
            )),
            new GCCCallExtractor(
                "gcc -include \"my/great file.h\" -include another/file.h"
            ).getCalls()
        );
    }

    @Test
    void defineSymbols() throws ParseException {
        assertEquals(List.of(new GCCCall(
                List.of(),
                List.of(),
                List.of(),
                Map.of("abc", "", "def", "ghi", "jkl", " mno", "pqr", "")
            )),
            new GCCCallExtractor(
                "gcc -Dabc -Ddef=ghi -Djkl=\" mno\" -Dpqr="
            ).getCalls()
        );
    }

    @Test
    void fileAfterIncludePath() throws ParseException {
        assertEquals(List.of(new GCCCall(
                List.of("code.c", "moreCode.c"),
                List.of("abc/def", "ghi/jkl"),
                List.of(),
                Map.of()
            )),
            new GCCCallExtractor(
                "gcc -Iabc/def code.c -I ghi/jkl moreCode.c"
            ).getCalls()
        );
    }

    @Test
    void fileAfterInclude() throws ParseException {
        assertEquals(List.of(new GCCCall(
                List.of("code.c"),
                List.of(),
                List.of("my/great/file.h"),
                Map.of()
            )),
            new GCCCallExtractor(
                "gcc -include my/great/file.h code.c"
            ).getCalls()
        );
    }

    @Test
    void multipleGCCCommands() throws ParseException {
        assertEquals(List.of(
                new GCCCall(
                    List.of(),
                    List.of("/path/to/unimportant/files"),
                    List.of(),
                    Map.of()
                ),
                new GCCCall(
                    List.of(),
                    List.of(),
                    List.of(),
                    Map.of()
                ),
                new GCCCall(
                    List.of(),
                    List.of(),
                    List.of(),
                    Map.of("ANSWER", "42")
                )
            ),
            new GCCCallExtractor(
                "gcc -I/path/to/unimportant/files; gcc\n gcc -DANSWER=42"
            ).getCalls()
        );
    }

    @Test
    void filtersNonGCCCommands() throws ParseException {
        assertEquals(List.of(), new GCCCallExtractor("set -e").getCalls());
        assertEquals(List.of(
                new GCCCall(
                    List.of(),
                    List.of(),
                    List.of("/etc/passwd"),
                    Map.of()
                ),
                new GCCCall(
                    List.of(),
                    List.of(),
                    List.of(),
                    Map.of()
                )
            ),
            new GCCCallExtractor(
                "gcc -include /etc/passwd; echo 'CC      noone@example.com'\t; gcc"
            ).getCalls()
        );
    }
}