package edu.kit.varijoern.cli;

import com.beust.jcommander.IStringConverter;

public class AnalysisStrategyConverter implements IStringConverter<AnalysisStrategy> {
    @Override
    public AnalysisStrategy convert(String value) {
        return switch (value.toLowerCase()) {
            case "product" -> AnalysisStrategy.PRODUCT;
            case "family" -> AnalysisStrategy.FAMILY;
            default -> throw new IllegalArgumentException("Unknown analysis strategy: " + value);
        };
    }
}
