package edu.kit.varijoern.config;

import org.jetbrains.annotations.NotNull;
import org.tomlj.TomlTable;

import java.util.Objects;

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
    @NotNull
    public static String getMandatoryString(String dottedName, TomlTable toml, String exceptionMessage)
            throws InvalidConfigException {
        if (!toml.isString(dottedName)) {
            throw new InvalidConfigException(exceptionMessage);
        }
        return Objects.requireNonNull(toml.getString(dottedName));
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
    public static int getMandatoryInt(String dottedName, TomlTable toml, String exceptionMessage)
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
