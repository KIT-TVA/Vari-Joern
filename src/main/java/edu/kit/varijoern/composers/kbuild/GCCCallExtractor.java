package edu.kit.varijoern.composers.kbuild;

import com.beust.jcommander.DynamicParameter;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import java.text.ParseException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Extracts GCC calls from a command string.
 */
public class GCCCallExtractor {
    private final static List<String> COMMAND_NAMES = List.of("gcc", "g++");
    // Match common C and C++ file names. Exclude files starting with a hyphen as they are probably actually GCC flags.
    private final static Pattern SOURCE_FILE_PATTERN = Pattern.compile("^[^-].*\\.(?:c|C|cc|cpp|cxx|c++)$");
    private final CommandParser parser;

    /**
     * Creates a new GCCCallExtractor which will parse the given command string.
     *
     * @param commandString the command string to parse. The command string is expected to be a list of commands
     *                      separated by newlines, semicolons or pipes.
     */
    public GCCCallExtractor(String commandString) {
        this.parser = new CommandParser(commandString);
    }

    /**
     * Extracts all GCC calls from the command string. Calls of other commands are ignored.
     *
     * @return a list of GCC calls.
     * @throws ParseException if the command string could not be parsed.
     */
    public List<GCCCall> getCalls() throws ParseException {
        List<GCCCall> calls = new ArrayList<>();
        for (List<String> command : this.parser.parse()) {
            if (!COMMAND_NAMES.contains(command.get(0))) continue;
            calls.add(this.parseCall(command));
        }
        return calls;
    }

    private GCCCall parseCall(List<String> command) {
        RawGCCCall rawCall = new RawGCCCall();
        List<String> preprocessedArguments = command.stream()
                .flatMap(arg -> {
                    if (arg.startsWith("-D") && arg.length() > 2 && arg.indexOf('=') == -1) {
                        return Stream.of(arg + "=");
                    } else if (arg.startsWith("-I") && arg.length() > 2) {
                        return Stream.of(arg.substring(0, 2), arg.substring(2));
                    } else {
                        return Stream.of(arg);
                    }
                })
                .toList();
        JCommander.newBuilder()
                .addObject(rawCall)
                .build()
                .parse(preprocessedArguments.subList(1, preprocessedArguments.size()).toArray(String[]::new));
        return new GCCCall(
                Optional.ofNullable(rawCall.compiledFiles)
                        .map(files -> files.stream()
                                .filter(file -> SOURCE_FILE_PATTERN.matcher(file).matches())
                                .toList()
                        )
                        .orElseGet(List::of),
                Objects.requireNonNullElseGet(rawCall.includePaths, List::of),
                Objects.requireNonNullElseGet(rawCall.includes, List::of),
                Objects.requireNonNullElseGet(rawCall.defines, Map::of)
        );
    }

    @SuppressWarnings("CanBeFinal") // JCommander requires non-final fields
    private static class RawGCCCall {
        @Parameter
        List<String> compiledFiles = new ArrayList<>();
        @Parameter(names = "-I")
        List<String> includePaths = new ArrayList<>();
        @Parameter(names = "-include")
        List<String> includes = new ArrayList<>();
        @DynamicParameter(names = "-D")
        Map<String, String> defines = new HashMap<>();
    }
}
