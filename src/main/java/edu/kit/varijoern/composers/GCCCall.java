package edu.kit.varijoern.composers;

import java.util.List;
import java.util.Map;

public record GCCCall(List<String> compiledFiles, List<String> includePaths, List<String> includes,
                      Map<String, String> defines) {

}
