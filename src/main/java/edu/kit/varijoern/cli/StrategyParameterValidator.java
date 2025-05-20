package edu.kit.varijoern.cli;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.ParameterException;

public class StrategyParameterValidator implements IParameterValidator {
    @Override
    public void validate(String name, String value) throws ParameterException {
        String lowerCaseValue = value.toLowerCase();
        if (!lowerCaseValue.equals("product") && !lowerCaseValue.equals("family")) {
            throw new ParameterException("Parameter %s must be either product or family (was %s)"
                    .formatted(name, lowerCaseValue));
        }
    }
}
