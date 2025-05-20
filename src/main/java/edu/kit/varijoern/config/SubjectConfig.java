package edu.kit.varijoern.config;

import org.jetbrains.annotations.NotNull;
import org.tomlj.TomlTable;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Class encapsulating configuration of the subject system to be used for the analysis.
 */
public class SubjectConfig {
    private static final @NotNull String NAME_KEY = "name";
    private static final @NotNull String SOURCE_ROOT_KEY = "source_root";

    /**
     * The name of the subject system.
     */
    private final @NotNull String subjectName;
    /**
     * The absolute {@link Path} to the root directory of the subject system.
     */
    private final @NotNull Path sourceRoot;

    /**
     * Constructor initializing the {@link SubjectConfig} with the fields contained in the subject table passed in as
     * parameter.
     *
     * @param subjectTable the subject table whose fields should be used to initialize the {@link SubjectConfig}.
     * @param configPath   the path to the path to the configuration file. Can be relative (will be interpreted relative
     *                     to the configPath).
     * @throws InvalidConfigException if a mandatory field of the {@link SubjectConfig} could not be initialized.
     */
    public SubjectConfig(@NotNull TomlTable subjectTable, @NotNull Path configPath) throws InvalidConfigException {
        this.subjectName = TomlUtils.getMandatoryString(SubjectConfig.NAME_KEY, subjectTable,
                "Subject system name was not specified");
        String sourceRoot = TomlUtils.getMandatoryString(SubjectConfig.SOURCE_ROOT_KEY, subjectTable,
                "Path to the root directory of the subject system was not specified");

        try {
            Path sourceRootPath = Path.of(sourceRoot);

            if (!sourceRootPath.isAbsolute()) {
                sourceRootPath = configPath.getParent().resolve(sourceRootPath);
            }

            this.sourceRoot = sourceRootPath;

            if (!Files.exists(this.sourceRoot)) {
                throw new InvalidConfigException("Specified root path \"" + this.sourceRoot + "\" does not exist.");
            }
        } catch (InvalidPathException invalidPathException) {
            throw new InvalidConfigException(invalidPathException.getMessage());
        }
    }

    /**
     * Gets the name of the subject system specified by this {@link SubjectConfig}
     *
     * @return the name of the subject system.
     */
    public @NotNull String getSubjectName() {
        return subjectName;
    }

    /**
     * Gets the absolute root path of the subject system specified by this {@link SubjectConfig}
     *
     * @return the absolute root path of the subject system.
     */
    public @NotNull Path getSourceRoot() {
        return sourceRoot;
    }
}
