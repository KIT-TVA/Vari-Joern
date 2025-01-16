package edu.kit.varijoern.config;

import org.jetbrains.annotations.NotNull;
import org.tomlj.TomlTable;

public class SugarlyzerConfig {
    private static final String ANALYZER_NAME_KEY = "analyzer_name";
    private static final String KEEP_INTERMEDIARY_FILES_KEY = "keep_intermediary_files";
    private static final String RELATIVE_PATHS_KEY = "relative_paths";

    private final @NotNull String analyzerName;
    private final boolean keepIntermediaryFiles;
    private final boolean relativePaths;

    public SugarlyzerConfig(@NotNull TomlTable sugarlyzerTable) throws InvalidConfigException {
        this.analyzerName = TomlUtils.getMandatoryString(ANALYZER_NAME_KEY, sugarlyzerTable, "Analyzer for Sugarlyzer was not specified");
        this.keepIntermediaryFiles = TomlUtils.getOptionalBoolean(KEEP_INTERMEDIARY_FILES_KEY, sugarlyzerTable).orElse(false);
        this.relativePaths = TomlUtils.getOptionalBoolean(RELATIVE_PATHS_KEY, sugarlyzerTable).orElse(false);
    }

    public @NotNull String getAnalyzerName() {
        return analyzerName;
    }

    public boolean getKeepIntermediaryFiles() {
        return keepIntermediaryFiles;
    }

    public boolean getRelativePaths() {
        return relativePaths;
    }
}
