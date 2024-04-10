package edu.kit.varijoern.composers;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Contains information about how to handle C/C++ code in a composition.
 */
public class CCPPLanguageInformation extends LanguageInformation {
    private final Map<Path, List<String>> includePaths;

    /**
     * Creates a new {@link CCPPLanguageInformation} instance.
     *
     * @param includePaths the include paths for the C/C++ code
     */
    public CCPPLanguageInformation(Map<Path, List<String>> includePaths) {
        this.includePaths = includePaths;
    }

    @Override
    public String getName() {
        return "C/C++";
    }

    @Override
    public void accept(LanguageInformationVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * Returns the include paths for the C/C++ code.
     *
     * @return the include paths for the C/C++ code
     */
    public Map<Path, List<String>> getIncludePaths() {
        return includePaths;
    }

    @Override
    public boolean equals(Object o) {
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
