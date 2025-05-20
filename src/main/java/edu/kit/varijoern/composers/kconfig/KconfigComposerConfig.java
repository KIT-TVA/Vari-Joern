package edu.kit.varijoern.composers.kconfig;

import edu.kit.varijoern.composers.Composer;
import edu.kit.varijoern.composers.ComposerArgs;
import edu.kit.varijoern.composers.ComposerConfig;
import edu.kit.varijoern.composers.ComposerException;
import edu.kit.varijoern.composers.kconfig.subjects.*;
import edu.kit.varijoern.config.InvalidConfigException;
import edu.kit.varijoern.config.SubjectConfig;
import org.jetbrains.annotations.NotNull;
import org.tomlj.TomlArray;
import org.tomlj.TomlInvalidTypeException;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Contains the configuration of the Kconfig composer.
 */
public class KconfigComposerConfig extends ComposerConfig {
    private static final String PATH_FIELD_NAME = "path";
    private static final String ENCODING_FIELD_NAME = "encoding";
    private static final String PRESENCE_CONDITION_EXCLUDES_FIELD_NAME = "presence_condition_excludes";

    private static final Set<String> SUPPORTED_SYSTEMS = Set.of("linux", "busybox", "fiasco", "axtls");

    private final @NotNull Path sourceLocation;
    private final @NotNull Charset encoding;
    private final @NotNull String system;
    private final @NotNull Set<Path> presenceConditionExcludes;
    private final ComposerArgs composerArgs;

    /**
     * Creates a new {@link KconfigComposerConfig} by extracting data from the specified TOML section.
     *
     * @param toml          the TOML section
     * @param subjectConfig the {@link SubjectConfig} with which to resolve sourceLocation if not absolute and to set
     *                      the system name.
     * @param composerArgs  the general command line arguments for the composer.
     * @throws InvalidConfigException if the TOML section does not represent a valid configuration
     */
    public KconfigComposerConfig(@NotNull TomlTable toml, @NotNull SubjectConfig subjectConfig,
                                 @NotNull ComposerArgs composerArgs) throws InvalidConfigException {
        super(toml);
        this.composerArgs = composerArgs;

        try {
            this.encoding = Charset.forName(toml.getString(ENCODING_FIELD_NAME, () -> "UTF-8"));
        } catch (IllegalArgumentException e) {
            throw new InvalidConfigException("Encoding for Kconfig composer is not supported", e);
        } catch (TomlInvalidTypeException e) {
            throw new InvalidConfigException("Encoding for Kconfig composer is not a string", e);
        }

        Path sourcePath;
        try {
            sourcePath = Path.of(toml.getString(PATH_FIELD_NAME, () -> "."));
        } catch (InvalidPathException e) {
            throw new InvalidConfigException("Source location for Kconfig composer is not a valid path", e);
        }
        if (!sourcePath.isAbsolute()) {
            sourcePath = subjectConfig.getSourceRoot().resolve(sourcePath);
        }

        this.sourceLocation = sourcePath;
        this.system = subjectConfig.getSubjectName();

        if (!SUPPORTED_SYSTEMS.contains(this.system)) {
            throw new InvalidConfigException("System for Kconfig composer is not supported");
        }

        if (toml.contains(PRESENCE_CONDITION_EXCLUDES_FIELD_NAME)) {
            if (!toml.isArray(PRESENCE_CONDITION_EXCLUDES_FIELD_NAME)) {
                throw new InvalidConfigException("Presence conditions excludes for Kconfig composer are not an array");
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
        ComposerStrategyFactory composerStrategyFactory = switch (this.system) {
            case "linux" -> new LinuxStrategyFactory();
            case "busybox" -> new BusyboxStrategyFactory();
            case "fiasco" -> new FiascoStrategyFactory();
            case "axtls" -> new AxtlsStrategyFactory();
            default -> throw new IllegalStateException("Unsupported system: " + this.system);
        };
        return new KconfigComposer(this.sourceLocation, composerStrategyFactory, this.encoding, tmpPath,
                this.presenceConditionExcludes, this.composerArgs.shouldSkipPCs());
    }
}
