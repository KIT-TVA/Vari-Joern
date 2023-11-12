package edu.kit.varijoern.composers.kbuild;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CommandParser {
    private final String commandString;
    private int cursor = 0;
    private List<List<String>> result;

    public CommandParser(String commandString) {
        this.commandString = commandString;
    }

    public List<List<String>> parse() throws ParseException {
        if (this.result == null) {
            List<List<String>> commands = new ArrayList<>();
            while (this.cursor < this.commandString.length()) {
                this.readSingleCommand().ifPresent(commands::add);
            }
            this.result = List.copyOf(commands);
        }
        return this.result;
    }

    private Optional<List<String>> readSingleCommand() throws ParseException {
        List<String> command = new ArrayList<>();
        while (this.cursor < this.commandString.length()) {
            char nextChar = this.commandString.charAt(this.cursor);
            if (isCommandSeparator(nextChar)) break;
            this.readArgument().ifPresent(command::add);
        }
        this.cursor++;
        if (command.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(List.copyOf(command));
        }
    }

    private static boolean isCommandSeparator(char character) {
        return character == '\n' || character == ';' || character == '|';
    }

    private Optional<String> readArgument() throws ParseException {
        while (this.cursor < this.commandString.length()) {
            char nextChar = this.commandString.charAt(this.cursor);
            if (nextChar != ' ' && nextChar != '\t') break;
            this.cursor++;
        }
        StringBuilder argument = new StringBuilder();
        int argumentStart = this.cursor;
        boolean readingQuotedString = false;
        char startingQuote = '"';

        charLoop:
        while (this.cursor < this.commandString.length()) {
            char currentChar = this.commandString.charAt(this.cursor);
            switch (currentChar) {
                case '\\':
                    this.cursor++;
                    if (this.cursor == this.commandString.length())
                        throw new ParseException("Command was terminated by `\\`", this.cursor - 1);
                    argument.append(this.commandString, argumentStart, this.cursor - 1);
                    argumentStart = this.cursor;
                    if (this.commandString.charAt(this.cursor) == '\n') {
                        // Escaped newline characters are ignored
                        argumentStart++;
                    }
                    break;
                case '\n':
                case ';':
                case '|':
                case ' ':
                case '\t':
                    if (!readingQuotedString)
                        break charLoop;
                    break;
                case '"':
                case '\'':
                    if (!readingQuotedString || currentChar == startingQuote) {
                        argument.append(this.commandString, argumentStart, this.cursor);
                        argumentStart = this.cursor + 1;
                        readingQuotedString = !readingQuotedString;
                        startingQuote = currentChar;
                    }
                    break;
                default:
                    // Nothing to do, currentChar will be added to argument automatically
            }
            this.cursor++;
        }
        if (readingQuotedString)
            throw new ParseException("Unterminated quote", this.cursor - 1);
        argument.append(this.commandString, argumentStart, this.cursor);
        if (argument.isEmpty())
            return Optional.empty();
        else
            return Optional.of(argument.toString());
    }
}
