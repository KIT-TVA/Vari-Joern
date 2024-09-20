package edu.kit.varijoern.composers.kbuild;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.text.ParseException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GCCCallExtractorTest {
    @Test
    void emptyString() throws ParseException {
        assertEquals(new GCCCallExtractor("").getCalls(), List.of());
    }

    @Test
    void noArguments() throws ParseException {
        assertEquals(List.of(new GCCCall(List.of(), List.of(), List.of(), List.of(), Map.of(), null)),
                new GCCCallExtractor("gcc").getCalls()
        );
    }

    @Test
    void singleFile() throws ParseException {
        assertEquals(List.of(new GCCCall(List.of("code.c"), List.of(), List.of(), List.of(), Map.of(), null)),
                new GCCCallExtractor("gcc code.c").getCalls()
        );
    }

    @Test
    void multipleFiles() throws ParseException {
        assertEquals(List.of(new GCCCall(List.of("code.c", "code2.c"), List.of(), List.of(), List.of(), Map.of(),
                        null)),
                new GCCCallExtractor("gcc code.c code2.c").getCalls()
        );
    }

    @Test
    void allArgumentTypes() throws ParseException {
        assertEquals(List.of(new GCCCall(
                        List.of("code.c"),
                        List.of("./my inc folder", "/usr/include"),
                        List.of("/usr/local/include"),
                        List.of("boot.h"),
                        Map.of("FOO", "abc", "__KERNEL__", ""),
                        null
                )),
                new GCCCallExtractor(
                        "gcc -I\"./my inc folder\" -isystem /usr/local/include -O2 -I/usr/include -DFOO=abc"
                                + " -D__KERNEL__ -include boot.h code.c"
                ).getCalls()
        );
    }

    @Test
    void includePath() throws ParseException {
        assertEquals(List.of(new GCCCall(
                        List.of(),
                        List.of("abc/def", "ghi/jkl"),
                        List.of(),
                        List.of(),
                        Map.of(),
                        null
                )),
                new GCCCallExtractor(
                        "gcc -Iabc/def -I ghi/jkl"
                ).getCalls()
        );
    }

    @Test
    void systemIncludePath() throws ParseException {
        assertEquals(List.of(new GCCCall(
                        List.of(),
                        List.of(),
                        List.of("/usr/include", "/usr/local/include"),
                        List.of(),
                        Map.of(),
                        null
                )),
                new GCCCallExtractor(
                        "gcc -isystem /usr/include -isystem /usr/local/include"
                ).getCalls()
        );
    }

    @Test
    void unsupportedIncludePaths() throws ParseException {
        assertEquals(List.of(new GCCCall(
                        List.of("source.c"),
                        List.of(),
                        List.of(),
                        List.of(),
                        Map.of(),
                        null
                )),
                new GCCCallExtractor(
                        "gcc -iquote my/inc/folder -idirafter /usr/local/include -iwithprefix /usr/include"
                                + "-iwithprefixbefore /usr/include source.c"
                ).getCalls()
        );
    }

    @Test
    void includeFile() throws ParseException {
        assertEquals(List.of(new GCCCall(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("my/great file.h", "another/file.h"),
                        Map.of(),
                        null
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
                        List.of(),
                        Map.of("abc", "", "def", "ghi", "jkl", " mno", "pqr", ""),
                        null
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
                        List.of(),
                        Map.of(),
                        null
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
                        List.of(),
                        List.of("my/great/file.h"),
                        Map.of(),
                        null
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
                                List.of(),
                                Map.of(),
                                null
                        ),
                        new GCCCall(
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                Map.of(),
                                null
                        ),
                        new GCCCall(
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                Map.of("ANSWER", "42"),
                                null
                        )
                ),
                new GCCCallExtractor(
                        "gcc -I/path/to/unimportant/files; gcc\n gcc -DANSWER=42 | tee stdout.txt"
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
                                List.of(),
                                List.of("/etc/passwd"),
                                Map.of(),
                                null
                        ),
                        new GCCCall(
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                Map.of(),
                                null
                        )
                ),
                new GCCCallExtractor(
                        "gcc -include /etc/passwd; echo 'CC      noone@example.com'\t; gcc"
                ).getCalls()
        );
    }

    @Test
    void recursiveMakeCall() throws ParseException {
        assertEquals(List.of(
                        new GCCCall(
                                List.of("code.c"),
                                List.of(),
                                List.of(),
                                List.of(),
                                Map.of(),
                                null
                        ),
                        new GCCCall(
                                List.of("more-code.c"),
                                List.of(),
                                List.of(),
                                List.of(),
                                Map.of(),
                                Path.of("/other/path")
                        ),
                        new GCCCall(
                                List.of("last-file.c"),
                                List.of(),
                                List.of(),
                                List.of(),
                                Map.of(),
                                Path.of("/last/path")
                        )
                ),
                new GCCCallExtractor(
                        """
                                gcc code.c
                                make[1]: Entering directory '/other/path'
                                gcc more-code.c
                                make[2]: Entering directory '/yet/another/path'
                                make[2]: Leaving directory '/yet/another/path'
                                make[2]: Entering directory '/last/path'
                                gcc last-file.c
                                make[2]: Leaving directory '/last/path'
                                make[1]: Leaving directory '/other/path'"""
                ).getCalls()
        );
    }
}