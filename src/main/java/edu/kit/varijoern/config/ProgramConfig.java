package edu.kit.varijoern.config;

import org.jetbrains.annotations.NotNull;
import org.tomlj.TomlTable;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class ProgramConfig {
    private final @NotNull Path programPath;

    public ProgramConfig(@NotNull TomlTable toml) throws InvalidConfigException {
        String programPath = Optional.ofNullable(toml.getString("source_path"))
                .filter(path -> !path.isEmpty())
                .orElseThrow(() -> new InvalidConfigException("Program path was not specified"));

        try {
            this.programPath = Paths.get(programPath);

            if(!Files.exists(this.programPath)){
                throw new InvalidConfigException("Specified program path \"" + this.programPath +"\" does not exist.");
            }
        } catch (InvalidPathException invalidPathException){
            throw new InvalidConfigException(invalidPathException.getMessage());
        }
    }

    public @NotNull Path getProgramPath() {
        return programPath;
    }
}
