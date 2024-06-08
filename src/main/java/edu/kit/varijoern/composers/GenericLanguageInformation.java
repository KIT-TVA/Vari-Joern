package edu.kit.varijoern.composers;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Indicates that the composer has no further information about the languages used in the composition.
 */
public class GenericLanguageInformation extends LanguageInformation {
    @Override
    public @NotNull String getName() {
        return "Generic";
    }

    @Override
    public <T extends Throwable> void accept(@NotNull LanguageInformationVisitor<T> visitor) throws T {
        visitor.visit(this);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        return o != null && getClass() == o.getClass();
    }
}
