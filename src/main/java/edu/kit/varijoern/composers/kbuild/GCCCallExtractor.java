package edu.kit.varijoern.composers.kbuild;

import org.apache.commons.cli.*;

import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;

public class GCCCallExtractor {
    private final CommandParser parser;
    private final Options options;
    private final CommandLineParser commandLineParser = new RelaxedParser();

    public GCCCallExtractor(String commandString) {
        this.parser = new CommandParser(commandString);
        this.options = new Options();
        this.options.addOption(
            Option.builder("D").hasArgs().valueSeparator().build()
        );
        this.options.addOption(
            Option.builder("I").hasArgs().build()
        );
        this.options.addOption(
            Option.builder("include").hasArg().build()
        );
    }

    public List<GCCCall> getCalls() throws ParseException {
        List<GCCCall> calls = new ArrayList<>();
        for (List<String> command : this.parser.parse()) {
            if (!command.get(0).equals("gcc")) continue;
            calls.add(this.parseCall(command));
        }
        return calls;
    }

    private GCCCall parseCall(List<String> command) throws ParseException {
        CommandLine commandLine;
        try {
            commandLine = this.commandLineParser.parse(this.options,
                command.subList(1, command.size()).toArray(String[]::new)
            );
        } catch (org.apache.commons.cli.ParseException e) {
            throw new ParseException("gcc call could not be parsed: %s".formatted(e.getMessage()), -1);
        }
        List<String> compiledFiles = Arrays.stream(Objects.requireNonNullElseGet(
                commandLine.getArgs(),
                () -> new String[0])
            )
            .filter(file -> file.endsWith(".c"))
            .toList();
        List<String> includedPaths = Arrays.stream(Objects.requireNonNullElseGet(
                commandLine.getOptionValues("I"),
                () -> new String[0])
            )
            .toList();
        List<String> includes = Arrays.stream(Objects.requireNonNullElseGet(
                commandLine.getOptionValues("include"),
                () -> new String[0])
            )
            .toList();
        Map<String, String> defines = Arrays.stream(commandLine.getOptions())
            .filter(option -> option.getOpt().equals("D"))
            .collect(Collectors.toMap(
                Option::getValue,
                option -> {
                    List<String> values = option.getValuesList();
                    if (values.size() < 2) return "";
                    return values.get(1);
                }
            ));
        return new GCCCall(compiledFiles, includedPaths, includes, defines);
    }

    // Source: https://stackoverflow.com/a/61332096,
    // License: https://creativecommons.org/licenses/by-sa/4.0/legalcode.en
    private static class RelaxedParser extends DefaultParser {
        @Override
        public CommandLine parse(final Options options, final String[] arguments)
            throws org.apache.commons.cli.ParseException {
            final List<String> knownArgs = new ArrayList<>();
            for (int i = 0; i < arguments.length; i++) {
                boolean argumentIsKnown;
                boolean nextArgumentIsKnown;
                if (!arguments[i].startsWith("-")) {
                    argumentIsKnown = true;
                    nextArgumentIsKnown = false;
                } else {
                    String argumentWithoutHyphens = arguments[i].startsWith("--")
                        ? arguments[i].substring(2)
                        : arguments[i].substring(1);
                    // Test if this is a Java option
                    Option potentialJavaOption = options.getOption(argumentWithoutHyphens.substring(0, 1));
                    if (potentialJavaOption != null && (potentialJavaOption.getArgs() >= 2
                        || potentialJavaOption.getArgs() == Option.UNLIMITED_VALUES)) {
                        // It is a Java option
                        nextArgumentIsKnown = argumentWithoutHyphens.length() == 1;
                        argumentIsKnown = true;
                    } else {
                        // It is not a Java option
                        argumentIsKnown = options.hasOption(argumentWithoutHyphens);
                        nextArgumentIsKnown = argumentIsKnown && i + 1 < arguments.length
                            && options.getOption(argumentWithoutHyphens).hasArg();
                    }
                }
                if (argumentIsKnown) {
                    knownArgs.add(arguments[i]);
                    if (nextArgumentIsKnown) {
                        knownArgs.add(arguments[++i]);
                    }
                }
            }
            return super.parse(options, knownArgs.toArray(new String[0]));
        }
    }
}
