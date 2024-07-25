package edu.kit.varijoern.config;

import org.jetbrains.annotations.NotNull;
import org.tomlj.TomlTable;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Class encapsulating configuration of the subject system to be used for the analysis.
 */
public class SubjectConfig {
    /**
     * The name of the subject system.
     */
    private final @NotNull String subjectName;
    /**
     * {@link Path} to the root directory of the subject system.
     */
    private final @NotNull Path rootPath;

    /**
     * Constructor initializing the {@link SubjectConfig} with the fields contained in the subject table passed in as parameter.
     *
     * @param subjectTable the subject table whose fields should be used to initialize the {@link SubjectConfig}.
     * @throws InvalidConfigException if a mandatory field of the {@link SubjectConfig} could not be initialized.
     */
    public SubjectConfig(@NotNull TomlTable subjectTable) throws InvalidConfigException {
        this.subjectName = Optional.ofNullable(subjectTable.getString("name"))
                .filter(name -> !name.isEmpty())
                .orElseThrow(() -> new InvalidConfigException("Subject system name was not specified"));

        String programPath = Optional.ofNullable(subjectTable.getString("root_path"))
                .filter(path -> !path.isEmpty())
                .orElseThrow(() -> new InvalidConfigException("Path to the root directory of the subject system was not specified"));

        try {
            this.rootPath = Paths.get(programPath);

            if (!Files.exists(this.rootPath)) {
                throw new InvalidConfigException("Specified root path \"" + this.rootPath + "\" does not exist.");
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
     * Gets the root path of the subject system specified by this {@link SubjectConfig}
     *
     * @return the root path of the subject system.
     */
    public @NotNull Path getRootPath() {
        return rootPath;
    }
}
