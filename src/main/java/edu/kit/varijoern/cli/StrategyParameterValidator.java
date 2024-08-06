package edu.kit.varijoern.cli;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.ParameterException;

public class StrategyParameterValidator implements IParameterValidator {
    @Override
    public void validate(String name, String value) throws ParameterException {
        value = value.toLowerCase();
        if (!value.equals("product") && !value.equals("family")) {
            throw new ParameterException("Parameter %s must be either product or family (was %s)".formatted(name, value));
        }
    }
}
