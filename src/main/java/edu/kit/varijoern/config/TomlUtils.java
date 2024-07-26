package edu.kit.varijoern.config;

import org.jetbrains.annotations.NotNull;
import org.tomlj.TomlTable;

import java.util.Objects;
import java.util.Optional;

/**
 * This utility class contains methods that are used for parsing TOML files.
 */
public final class TomlUtils {
    private TomlUtils() {
        throw new IllegalStateException();
    }

    /**
     * Extracts a string value from the specified TOML section.
     *
     * @param dottedName       the key of the string
     * @param toml             the TOML section
     * @param exceptionMessage the message used for the exception thrown on failure
     * @return the string value
     * @throws InvalidConfigException if the value is not present or not a string
     */
    public static @NotNull String getMandatoryString(@NotNull String dottedName, @NotNull TomlTable toml,
                                                     @NotNull String exceptionMessage)
            throws InvalidConfigException {
        return Optional.ofNullable(toml.getString(dottedName))
                .filter(value -> !value.isEmpty())
                .orElseThrow(() -> new InvalidConfigException(exceptionMessage));
    }

    /**
     * Extracts an int value from the specified TOML section.
     *
     * @param dottedName       the key of the string
     * @param toml             the TOML section
     * @param exceptionMessage the message used for the exception thrown on failure
     * @return the string value
     * @throws InvalidConfigException if the value is not present, not an integer or out of range for the int type
     */
    public static int getMandatoryInt(@NotNull String dottedName, @NotNull TomlTable toml,
                                      @NotNull String exceptionMessage)
            throws InvalidConfigException {
        if (!toml.isLong(dottedName)) {
            throw new InvalidConfigException(exceptionMessage);
        }
        long value = Objects.requireNonNull(toml.getLong(dottedName));
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new InvalidConfigException(exceptionMessage);
        }
        return (int) value;
    }
}
