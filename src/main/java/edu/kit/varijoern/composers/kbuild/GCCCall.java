package edu.kit.varijoern.composers.kbuild;

import java.util.List;
import java.util.Map;

/**
 * Represents a call to GCC.
 */
public record GCCCall(List<String> compiledFiles, List<String> includePaths, List<String> includes,
                      Map<String, String> defines) {

}
