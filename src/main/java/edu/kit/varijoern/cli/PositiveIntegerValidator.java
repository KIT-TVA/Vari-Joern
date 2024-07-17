package edu.kit.varijoern.cli;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.ParameterException;

/**
 * A validator for positive integers (i.e., > 0).
 */
public class PositiveIntegerValidator implements IParameterValidator {
    @Override
    public void validate(String name, String value) throws ParameterException {
        int n = Integer.parseInt(value);
        if (n <= 0)
            throw new ParameterException("Parameter %s should be positive (was %d)".formatted(name, n));
    }
}
