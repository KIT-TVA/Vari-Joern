package edu.kit.varijoern.composers.kbuild;

import edu.kit.varijoern.composers.CommandParser;
import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandParserTest {
    @Test
    void emptyString() throws ParseException {
        assertEquals(List.of(), new CommandParser("").parse());
    }

    @Test
    void singleWord() throws ParseException {
        assertEquals(List.of(List.of("abc")), new CommandParser("abc").parse());
    }

    @Test
    void withArgument() throws ParseException {
        assertEquals(List.of(List.of("abc", "def")), new CommandParser("abc def").parse());
    }

    @Test
    void escapeNewline() throws ParseException {
        assertEquals(List.of(List.of("abc", "def")), new CommandParser("abc \\\ndef").parse());
        assertEquals(List.of(List.of("abc")), new CommandParser("\"ab\\\nc\"").parse());
    }

    @Test
    void multipleWhitespaceCharacters() throws ParseException {
        assertEquals(List.of(List.of("abc", "def", "ghi", "jkl")),
            new CommandParser("   \t abc \tdef\t ghi  jkl ").parse());
    }

    @Test
    void multipleCommands() throws ParseException {
        assertEquals(List.of(List.of("abc", "def"), List.of("ghi", "jkl")),
            new CommandParser("abc def \n ghi jkl").parse());
        assertEquals(List.of(List.of("abc", "def"), List.of("ghi", "jkl")),
            new CommandParser("abc def ; ghi jkl").parse());
        assertEquals(List.of(List.of("abc", "def"), List.of("ghi", "jkl")),
            new CommandParser("abc def\nghi jkl").parse());
        assertEquals(List.of(List.of("abc", "def"), List.of("ghi", "jkl")),
            new CommandParser(("abc def;ghi jkl")).parse());
        assertEquals(List.of(List.of("abc", "def"), List.of("ghi", "jkl")),
            new CommandParser(("abc def|ghi jkl")).parse());
    }

    @Test
    void ignoreEmptyCommands() throws ParseException {
        assertEquals(List.of(List.of("abc", "def"), List.of("ghi", "jkl")),
            new CommandParser("abc def\n\nghi jkl").parse());
        assertEquals(List.of(List.of("abc", "def"), List.of("ghi", "jkl")),
            new CommandParser("abc def;; ghi jkl").parse());
        assertEquals(List.of(List.of("abc", "def")),
            new CommandParser("abc def\n").parse());
        assertEquals(List.of(List.of("abc", "def")),
            new CommandParser("\nabc def").parse());
        assertEquals(List.of(), new CommandParser("\n;").parse());
    }

    @Test
    void quotes() throws ParseException {
        assertEquals(List.of(List.of("abc", "def ghi")),
            new CommandParser("abc 'def ghi'").parse());
        assertEquals(List.of(List.of("abc\ndef")),
            new CommandParser("'abc\ndef'").parse());
        assertEquals(List.of(List.of("abc def")),
            new CommandParser("\"abc def\"").parse());
        assertEquals(List.of(List.of("gcc", "-I./my folder")),
            new CommandParser("gcc -I\"./my folder\"").parse());
    }

    @Test
    void escapeQuotes() throws ParseException {
        assertEquals(List.of(List.of("abc", "\"def")),
            new CommandParser("abc \\\"def").parse());
        assertEquals(List.of(List.of("abc", "def\"")),
            new CommandParser("abc def\\\"").parse());
    }

    @Test
    void escapeInQuotedStrings() throws ParseException {
        assertEquals(List.of(List.of("abc", "\"def ghi")),
            new CommandParser("abc \"\\\"def ghi\"").parse());
        assertEquals(List.of(List.of("abc\"def")),
            new CommandParser("abc\\\"def").parse());
    }

    @Test
    void escapeNormalCharacter() throws ParseException {
        assertEquals(List.of(List.of("abc")),
            new CommandParser("a\\bc").parse());
        assertEquals(List.of(List.of("abc")),
            new CommandParser("\"a\\bc\"").parse());
    }

    @Test
    void unterminatedQuotedString() {
        assertThrows(ParseException.class, () -> new CommandParser("\"abc").parse());
        assertThrows(ParseException.class, () -> new CommandParser("abc \"def ghi").parse());
        assertThrows(ParseException.class, () -> new CommandParser("abc \"def\\\"").parse());
    }

    @Test
    void terminatingBackslash() {
        assertThrows(ParseException.class, () -> new CommandParser("\\").parse());
        assertThrows(ParseException.class, () -> new CommandParser("abc\\").parse());
    }

    @Test
    void parseTwice() throws ParseException {
        CommandParser parser = new CommandParser("abc def");
        List<List<String>> expectedResult = List.of(List.of("abc", "def"));
        assertEquals(expectedResult, parser.parse());
        assertEquals(expectedResult, parser.parse());
    }
}