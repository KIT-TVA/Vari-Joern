package edu.kit.varijoern.config;

import org.jetbrains.annotations.NotNull;
import org.tomlj.TomlTable;

public class SugarlyzerConfig {
    private static final String ANALYZER_NAME_KEY = "analyzer_name";
    private final @NotNull String analyzerName;

    public SugarlyzerConfig(@NotNull TomlTable sugarlyzerTable) throws InvalidConfigException {
        this.analyzerName = TomlUtils.getMandatoryString(ANALYZER_NAME_KEY, sugarlyzerTable, "Analyzer for Sugarlyzer was not specified");
    }

    public @NotNull String getAnalyzerName() {
        return analyzerName;
    }
}
