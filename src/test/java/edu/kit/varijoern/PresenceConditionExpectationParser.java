package edu.kit.varijoern;

import org.jetbrains.annotations.NotNull;
import org.prop4j.And;
import org.prop4j.Node;
import org.prop4j.NodeReader;

import java.nio.file.Path;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A parser for strings containing presence condition expectations for multiple files.
 * <h1>Format</h1>
 * <p>
 * The format is line-based. The presence conditions for the files are given one after the other. Each file is
 * introduced by a line starting with {@code file:} followed by the relative file path and a presence condition,
 * separated from the file name by a comma. The presence condition is a propositional formula in the "short symbol"
 * syntax of the
 * <a href="https://github.com/FeatureIDE/FeatureIDE/tree/423fa0a1b3cdecb3d00826ce7d32a264cc1ff078/plugins/de.ovgu.featureide.fm.core/src/org/prop4j">prop4j</a>
 * library.
 * </p>
 * <p>
 * After the file line, there are lines specifying the presence conditions for individual lines in the file.
 * Each line has the format {@code <start>-<end>? <presence condition>} or
 * {@code <start>-<end>! <presence condition>}, where {@code <start>} and {@code <end>} are the start and end
 * line numbers (both inclusive) of the range of lines to which the presence condition applies,
 * {@code <presence condition>} is a propositional formula as above, and {@code ?} or {@code !} indicates
 * whether the presence condition is optional ({@code ?}) or not ({@code !}), see
 * {@link PresenceConditionExpectation#isOptional()}.
 * </p>
 * <p>
 * The end line number may be omitted, in which case the presence condition applies only to the start line. In this
 * case, the line has the format {@code <start>? <presence condition>} or {@code <start>! <presence condition>}.
 * </p>
 * <p>
 * The presence condition for a line is the conjunction of the presence condition for the file and the presence
 * condition for the line. Multiple presence conditions for the same line are not allowed. Not specifying a presence
 * condition between two specified presence conditions is also not allowed.
 * </p>
 */
public class PresenceConditionExpectationParser {
    public static final String FILE_PREFIX = "file:";
    private static final Pattern LINE_CONDITION_PATTERN = Pattern.compile("([0-9]+)(?:-([0-9]+))?\\s*([?!])\\s*((?:[a-zA-Z0-9_\\-&|]*\\s*)*)$");

    /**
     * Parses a string containing presence condition expectations for multiple files.
     *
     * @param text the string to parse
     * @return a map from file paths to lists of presence condition expectations. In each list, the presence condition
     * expectation for line {@code i} is at index {@code i - 1} of the list.
     * @throws ParseException if the string could not be parsed
     */
    public static Map<Path, List<PresenceConditionExpectation>> parse(@NotNull String text) throws ParseException {
        Map<Path, List<PresenceConditionExpectation>> result = new HashMap<>();
        Path currentFile = null;
        List<PresenceConditionExpectation> currentExpectations = null;
        Node currentFilePresenceCondition = null;
        int currentLineOffset;
        int currentLineNumber = 0;
        int nextLineOffset = 0;

        for (String line : text.split("\n")) {
            currentLineNumber++;
            currentLineOffset = nextLineOffset;
            nextLineOffset = currentLineOffset + line.length() + 1;
            int startOfComment = line.indexOf("#");
            if (startOfComment != -1) {
                line = line.substring(0, startOfComment);
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("file:")) {
                if (currentFile != null) {
                    addFileExpectations(result, currentFile, currentExpectations, nextLineOffset);
                }
                int presenceConditionStart = line.lastIndexOf(",");
                if (presenceConditionStart == -1) {
                    throw new ParseException("Expected presence condition after file path", nextLineOffset - 1);
                }
                currentFile = Path.of(line.substring(FILE_PREFIX.length(), presenceConditionStart).trim());
                if (currentFile.isAbsolute())
                    throw new ParseException("File path must be relative", nextLineOffset - 1);
                if (result.containsKey(currentFile)) {
                    throw new ParseException("Duplicate file path", nextLineOffset - 1);
                }
                currentExpectations = new ArrayList<>();
                currentFilePresenceCondition = readNode(
                        line.substring(presenceConditionStart + 1).trim()
                );
                continue;
            }
            if (currentFile == null) {
                throw new ParseException("Expected file path", currentLineOffset);
            }
            Matcher lineConditionMatcher = LINE_CONDITION_PATTERN.matcher(line);
            if (!lineConditionMatcher.matches()) {
                throw new ParseException("Could not parse line condition", currentLineOffset);
            }
            int startLine = Integer.parseInt(lineConditionMatcher.group(1));
            int endLine = lineConditionMatcher.group(2) == null
                    ? startLine
                    : Integer.parseInt(lineConditionMatcher.group(2));
            boolean optional = lineConditionMatcher.group(3).equals("?");
            Node presenceCondition = new And(
                    currentFilePresenceCondition,
                    readNode(lineConditionMatcher.group(4))
            );
            if (currentExpectations.size() < endLine) {
                currentExpectations.addAll(
                        Stream.generate(() -> (PresenceConditionExpectation) null)
                                .limit(endLine - currentExpectations.size())
                                .toList()
                );
            }
            for (int i = startLine; i <= endLine; i++) {
                if (currentExpectations.get(i - 1) != null) {
                    throw new ParseException(
                            "Duplicate line condition for %s:%d".formatted(currentFile, currentLineNumber),
                            currentLineOffset
                    );
                }
                currentExpectations.set(i - 1, new PresenceConditionExpectation(optional, presenceCondition));
            }
        }
        if (currentFile != null) {
            addFileExpectations(result, currentFile, currentExpectations, nextLineOffset);
        }
        return result;
    }

    private static void addFileExpectations(@NotNull Map<Path, List<PresenceConditionExpectation>> result,
                                            @NotNull Path currentFile,
                                            @NotNull List<PresenceConditionExpectation> currentExpectations,
                                            int nextLineOffset) throws ParseException {
        if (currentExpectations.stream().anyMatch(Objects::isNull))
            throw new ParseException("Not all lines have presence conditions", nextLineOffset - 1);
        result.put(currentFile, currentExpectations);
    }

    private static Node readNode(String condition) {
        NodeReader nodeReader = new NodeReader();
        nodeReader.activateShortSymbols();
        return nodeReader.stringToNode(condition);
    }
}
