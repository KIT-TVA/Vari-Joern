package edu.kit.varijoern.config;

import org.jetbrains.annotations.NotNull;
import org.tomlj.TomlTable;

import java.util.Objects;

public final class TomlUtils {
    private TomlUtils() {
        throw new IllegalStateException();
    }

    @NotNull
    public static String getMandatoryString(String dottedName, TomlTable toml, String exceptionMessage) throws InvalidConfigException {
        if (!toml.isString(dottedName)) {
            throw new InvalidConfigException(exceptionMessage);
        }
        return Objects.requireNonNull(toml.getString(dottedName));
    }
}
