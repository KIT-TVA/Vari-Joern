package edu.kit.varijoern.composers.kconfig;

import com.beust.jcommander.DynamicParameter;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Extracts GCC calls from a command string.
 */
public class GCCCallExtractor {
    private static final List<String> COMMAND_PREFIXES = List.of("", "x86_64-linux-gnu-");
    private static final List<String> COMMAND_NAMES = List.of("gcc", "g++");
    // Match common C and C++ file names. Exclude files starting with a hyphen as they are probably actually GCC flags.
    private static final Pattern SOURCE_FILE_PATTERN = Pattern.compile("^[^-].*\\.(?:c|C|cc|cpp|cxx|c++)$");
    private static final Set<String> UNSUPPORTED_INCLUDE_OPTIONS = Set.of("-iquote", "-idirafter", "-I-",
            "-iwithprefix", "-iwithprefixbefore");
    private static final Logger LOGGER = LogManager.getLogger();
    private final @NotNull CommandParser parser;

    /**
     * Creates a new GCCCallExtractor which will parse the given command string.
     *
     * @param commandString the command string to parse. The command string is expected to be a list of commands
     *                      separated by newlines, semicolons or pipes.
     */
    public GCCCallExtractor(@NotNull String commandString) {
        this.parser = new CommandParser(commandString);
    }

    /**
     * Extracts all GCC calls from the command string. Calls of other commands are ignored.
     *
     * @return a list of GCC calls.
     * @throws ParseException if the command string could not be parsed.
     */
    public @NotNull List<GCCCall> getCalls() throws ParseException {
        List<GCCCall> calls = new ArrayList<>();
        List<Path> makePathStack = new ArrayList<>();
        for (List<String> command : this.parser.parse()) {
            if (command.size() > 1 && command.get(0).matches("make\\[\\d+]:")) {
                Path path = Path.of(command.get(3));
                if (command.get(1).equals("Entering")) {
                    makePathStack.add(path);
                } else if (command.get(1).equals("Leaving")) {
                    if (!makePathStack.get(makePathStack.size() - 1).equals(path)) {
                        LOGGER.warn("Mismatched make path: {}", String.join(" ", command));
                    }
                    makePathStack.remove(makePathStack.size() - 1);
                } else {
                    LOGGER.warn("Ignoring make information: {}", String.join(" ", command));
                }
                continue;
            }
            if (!isRelevantCommand(command.get(0))) continue;
            calls.add(this.parseCall(command, makePathStack.isEmpty()
                    ? null
                    : makePathStack.get(makePathStack.size() - 1)));
        }
        return calls;
    }

    private static boolean isRelevantCommand(@NotNull String commandName) {
        return COMMAND_PREFIXES.stream()
                .anyMatch(prefix -> commandName.startsWith(prefix)
                        && COMMAND_NAMES.contains(commandName.substring(prefix.length()))
                );
    }

    private @NotNull GCCCall parseCall(@NotNull List<String> command, @Nullable Path currentDirectory) {
        RawGCCCall rawCall = new RawGCCCall();
        List<String> unsupportedIncludeOptions = new ArrayList<>();
        List<String> preprocessedArguments = command.stream()
                .flatMap(arg -> {
                    if (UNSUPPORTED_INCLUDE_OPTIONS.contains(arg)) {
                        unsupportedIncludeOptions.add(arg);
                        return Stream.of(arg);
                    }
                    if (arg.startsWith("-D") && arg.length() > 2 && arg.indexOf('=') == -1) {
                        return Stream.of(arg + "=");
                    } else if (arg.startsWith("-I") && arg.length() > 2) {
                        return Stream.of(arg.substring(0, 2), arg.substring(2));
                    } else {
                        return Stream.of(arg);
                    }
                })
                .toList();
        if (!unsupportedIncludeOptions.isEmpty()) {
            LOGGER.warn("Ignoring unsupported include options: {}", String.join(", ", unsupportedIncludeOptions));
        }
        JCommander.newBuilder()
                .addObject(rawCall)
                .build()
                .parse(preprocessedArguments.subList(1, preprocessedArguments.size()).toArray(String[]::new));
        return new GCCCall(
                rawCall.compiledFiles
                        .stream()
                        .filter(file -> SOURCE_FILE_PATTERN.matcher(file).matches())
                        .toList(),
                rawCall.includePaths,
                rawCall.systemIncludePaths,
                rawCall.includes,
                rawCall.defines,
                currentDirectory
        );
    }

    @SuppressWarnings("CanBeFinal") // JCommander requires non-final fields
    private static class RawGCCCall {
        @Parameter
        @NotNull
        List<String> compiledFiles = new ArrayList<>();

        @Parameter(names = "-I")
        @NotNull
        List<String> includePaths = new ArrayList<>();

        @Parameter(names = "-isystem")
        @NotNull
        List<String> systemIncludePaths = new ArrayList<>();

        @Parameter(names = "-include")
        @NotNull
        List<String> includes = new ArrayList<>();

        @DynamicParameter(names = "-D")
        @NotNull
        Map<String, String> defines = new HashMap<>();
    }
}
