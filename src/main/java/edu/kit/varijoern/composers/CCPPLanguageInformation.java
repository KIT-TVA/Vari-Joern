package edu.kit.varijoern.composers;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Contains information about how to handle C/C++ code in a composition.
 */
public class CCPPLanguageInformation extends LanguageInformation {
    private final @NotNull Map<Path, List<String>> includePaths;

    /**
     * Creates a new {@link CCPPLanguageInformation} instance.
     *
     * @param includePaths the include paths for the C/C++ code
     */
    public CCPPLanguageInformation(@NotNull Map<Path, List<String>> includePaths) {
        this.includePaths = includePaths;
    }

    @Override
    public @NotNull String getName() {
        return "C/C++";
    }

    @Override
    public <T extends Throwable> void accept(@NotNull LanguageInformationVisitor<T> visitor) throws T {
        visitor.visit(this);
    }

    /**
     * Returns the include paths for the C/C++ code.
     *
     * @return the include paths for the C/C++ code
     */
    public @NotNull Map<Path, List<String>> getIncludePaths() {
        return includePaths;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CCPPLanguageInformation that = (CCPPLanguageInformation) o;
        return Objects.equals(includePaths, that.includePaths);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(includePaths);
    }
}
