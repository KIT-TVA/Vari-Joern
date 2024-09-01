package edu.kit.varijoern;

/**
 * Metadata for a Kconfig test case.
 *
 * @param encoding the encoding of the test case source files. This does not apply to the feature model or presence
 *                 conditions.
 */
public record KconfigTestCaseMetadata(String encoding) {
}
