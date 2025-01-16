package edu.kit.varijoern.cli;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.ParameterException;
import edu.kit.varijoern.output.JSONOutputFormatter;
import edu.kit.varijoern.output.OutputFormatter;
import edu.kit.varijoern.output.TextOutputFormatter;
import org.jetbrains.annotations.NotNull;

/**
 * Converts a string to the {@link OutputFormatter} it specifies.
 */
public class OutputFormatterConverter implements IStringConverter<OutputFormatter> {
    @Override
    public @NotNull OutputFormatter convert(@NotNull String s) {
        return switch (s) {
            case "text" -> new TextOutputFormatter();
            case "json" -> new JSONOutputFormatter();
            default -> throw new ParameterException("Unknown output format: " + s);
        };
    }
}
