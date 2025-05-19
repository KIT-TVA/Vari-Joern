package edu.kit.varijoern.composers.kconfig;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Represents a call to GCC.
 */
public record GCCCall(List<String> compiledFiles, List<String> includePaths, List<String> systemIncludePaths,
                      List<String> includes, Map<String, String> defines, @Nullable Path workingDirectory) {
}
