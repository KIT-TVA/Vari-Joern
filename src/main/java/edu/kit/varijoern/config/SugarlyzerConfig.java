package edu.kit.varijoern.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tomlj.TomlTable;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class SugarlyzerConfig {
    private final @Nullable Path sugarlyzerPath;

    public SugarlyzerConfig(@NotNull TomlTable toml) throws InvalidConfigException {
        Optional<String> programPath = Optional.ofNullable(toml.getString("path"))
                .filter(path -> !path.isEmpty());

        if (programPath.isPresent()) {
            try {
                this.sugarlyzerPath = Paths.get(programPath.get());

                if (!Files.exists(this.sugarlyzerPath)) {
                    throw new InvalidConfigException("Specified sugarlyzer path \"" + this.sugarlyzerPath + "\" does not exist.");
                }
            } catch (InvalidPathException invalidPathException) {
                throw new InvalidConfigException(invalidPathException.getMessage());
            }
        } else {
            this.sugarlyzerPath = null;
        }
    }

    public @Nullable Path getSugarlyzerPath() {
        return this.sugarlyzerPath;
    }
}
