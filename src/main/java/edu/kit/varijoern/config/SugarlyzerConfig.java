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
    private final @Nullable Path supercPath;

    public SugarlyzerConfig(@NotNull TomlTable toml) throws InvalidConfigException {
        this.sugarlyzerPath = this.validateAndGetPath(toml, "path");
        this.supercPath = this.validateAndGetPath(toml, "superc_path");
    }

    public @Nullable Path getSugarlyzerPath() {
        return this.sugarlyzerPath;
    }

    public @Nullable Path getSupercPath() {
        return this.supercPath;
    }

    private @Nullable Path validateAndGetPath(@NotNull TomlTable toml, @NotNull String dottedKey) throws InvalidConfigException {
        Optional<String> supercPath = Optional.ofNullable(toml.getString(dottedKey))
                .filter(path -> !path.isEmpty());

        if (supercPath.isPresent()) {
            try {
                Path path = Paths.get(supercPath.get());

                if (!Files.exists(path)) {
                    throw new InvalidConfigException("Specified path \"" + this.supercPath + "\" for key \"" + dottedKey + "\" does not exist.");
                }

                return path;
            } catch (InvalidPathException invalidPathException) {
                throw new InvalidConfigException(invalidPathException.getMessage());
            }
        }

        return null;
    }
}
