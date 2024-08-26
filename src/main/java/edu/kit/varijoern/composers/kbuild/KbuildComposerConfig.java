package edu.kit.varijoern.composers.kbuild;

import edu.kit.varijoern.composers.Composer;
import edu.kit.varijoern.composers.ComposerConfig;
import edu.kit.varijoern.composers.ComposerException;
import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.TomlUtils;
import org.jetbrains.annotations.NotNull;
import org.tomlj.TomlArray;
import org.tomlj.TomlInvalidTypeException;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Contains the configuration of the Kbuild composer.
 */
public class KbuildComposerConfig extends ComposerConfig {
    private static final String SOURCE_FIELD_NAME = "source";
    private static final String SYSTEM_FIELD_NAME = "system";
    private static final String PRESENCE_CONDITION_EXCLUDES_FIELD_NAME = "presence_condition_excludes";

    private final @NotNull Path sourceLocation;
    private final @NotNull String system;
    private final @NotNull Set<Path> presenceConditionExcludes;

    /**
     * Creates a new {@link KbuildComposerConfig} by extracting data from the specified TOML section.
     * Relative paths are assumed to be relative to the specified path of the configuration file.
     *
     * @param toml       the TOML section
     * @param configPath the path to the configuration file. Must be absolute.
     * @throws InvalidConfigException if the TOML section does not represent a valid configuration
     */
    public KbuildComposerConfig(@NotNull TomlTable toml, @NotNull Path configPath) throws InvalidConfigException {
        super(toml);
        String sourceLocation = TomlUtils.getMandatoryString(
                SOURCE_FIELD_NAME,
                toml,
                "Source location for Kbuild composer is missing or not a string"
        );
        Path sourcePath;
        try {
            sourcePath = Path.of(sourceLocation);
        } catch (InvalidPathException e) {
            throw new InvalidConfigException("Source location for Kbuild composer is not a valid path", e);
        }
        if (!sourcePath.isAbsolute()) {
            sourcePath = configPath.getParent().resolve(sourcePath);
        }
        this.sourceLocation = sourcePath;

        this.system = TomlUtils.getMandatoryString(
                SYSTEM_FIELD_NAME,
                toml,
                "System for Kbuild composer is missing or not a string"
        );
        if (!KbuildComposer.isSupportedSystem(this.system)) {
            throw new InvalidConfigException("System for Kbuild composer is not supported");
        }

        if (toml.contains(PRESENCE_CONDITION_EXCLUDES_FIELD_NAME)) {
            if (!toml.isArray(PRESENCE_CONDITION_EXCLUDES_FIELD_NAME)) {
                throw new InvalidConfigException("Presence conditions excludes for Kbuild composer are not an array");
            }

            List<Path> excludedPathsList = new ArrayList<>();
            TomlArray excludedPathsTomlArray = Objects.requireNonNull(
                    toml.getArray(PRESENCE_CONDITION_EXCLUDES_FIELD_NAME)
            );

            for (int i = 0; i < excludedPathsTomlArray.size(); i++) {
                String pathString;
                try {
                    pathString = excludedPathsTomlArray.getString(i);
                } catch (TomlInvalidTypeException e) {
                    throw new InvalidConfigException("A specified presence conditions exclude path is not a string", e);
                }
                Path path;
                try {
                    path = Path.of(pathString);
                } catch (InvalidPathException e) {
                    throw new InvalidConfigException("A specified presence conditions exclude path is not a valid path",
                            e);
                }
                if (path.isAbsolute())
                    throw new InvalidConfigException("A specified presence conditions exclude path is not relative");
                excludedPathsList.add(path);
            }
            this.presenceConditionExcludes = Set.copyOf(excludedPathsList);
        } else {
            this.presenceConditionExcludes = Set.of();
        }
    }

    @Override
    public @NotNull Composer newComposer(@NotNull Path tmpPath)
            throws IOException, ComposerException, InterruptedException {
        return new KbuildComposer(this.sourceLocation, this.system, tmpPath, this.presenceConditionExcludes);
    }
}
