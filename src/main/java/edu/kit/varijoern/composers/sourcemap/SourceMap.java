package edu.kit.varijoern.composers.sourcemap;

import java.util.Optional;

public interface SourceMap {
    /**
     * Returns the location in the original source code of the line specified relative to the directory containing the
     * composed code. If the location cannot be determined, an empty {@link Optional} is returned.
     *
     * @param location the location in the composed code
     * @return the location in the original code, relative to the directory containing the original code, or an empty
     * {@link Optional}
     */
    Optional<SourceLocation> getOriginalLocation(SourceLocation location);
}
