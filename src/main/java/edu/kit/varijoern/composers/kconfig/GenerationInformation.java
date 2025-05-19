package edu.kit.varijoern.composers.kconfig;

import java.nio.file.Path;

/**
 * Contains information about the generation of a file by a {@link KconfigComposer}.
 *
 * @param originalPath the path to the original file relative to the source directory
 * @param addedLines   the number of lines added to the file by the composer
 */
public record GenerationInformation(Path originalPath, int addedLines) {
}
