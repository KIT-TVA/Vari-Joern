package edu.kit.varijoern.composers.sourcemap;

import java.util.Optional;

/**
 * A {@link SourceMap} that maps all locations to themselves.
 */
public class IdentitySourceMap implements SourceMap {
    @Override
    public Optional<SourceLocation> getOriginalLocation(SourceLocation location) {
        return Optional.of(location);
    }
}
