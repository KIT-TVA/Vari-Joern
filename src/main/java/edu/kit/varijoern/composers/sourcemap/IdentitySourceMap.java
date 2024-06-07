package edu.kit.varijoern.composers.sourcemap;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * A {@link SourceMap} that maps all locations to themselves.
 */
public class IdentitySourceMap implements SourceMap {
    @Override
    public @NotNull Optional<SourceLocation> getOriginalLocation(@NotNull SourceLocation location) {
        return Optional.of(location);
    }
}
