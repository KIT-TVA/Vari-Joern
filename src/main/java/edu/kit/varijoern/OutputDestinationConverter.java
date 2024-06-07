package edu.kit.varijoern;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.ParameterException;
import org.jetbrains.annotations.NotNull;

import java.nio.file.InvalidPathException;

/**
 * Converts a string to an {@link OutputDestination}. For more information about the format, see
 * {@link OutputDestination#OutputDestination(String)}.
 */
public class OutputDestinationConverter implements IStringConverter<OutputDestination> {
    @Override
    public @NotNull OutputDestination convert(@NotNull String s) {
        try {
            return new OutputDestination(s);
        } catch (InvalidPathException e) {
            throw new ParameterException("Invalid path: " + s, e);
        }
    }
}
