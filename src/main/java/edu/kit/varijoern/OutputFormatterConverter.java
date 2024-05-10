package edu.kit.varijoern;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.ParameterException;
import edu.kit.varijoern.output.OutputFormatter;
import edu.kit.varijoern.output.TextOutputFormatter;

/**
 * Converts a string to the {@link OutputFormatter} it specifies.
 */
public class OutputFormatterConverter implements IStringConverter<OutputFormatter> {
    @Override
    public OutputFormatter convert(String s) {
        switch (s) {
            case "text":
                return new TextOutputFormatter();
            default:
                throw new ParameterException("Unknown output format: " + s);
        }
    }
}
