package edu.kit.varijoern.composers;

/**
 * Indicates that the composer has no further information about the languages used in the composition.
 */
public class GenericLanguageInformation extends LanguageInformation {
    @Override
    public String getName() {
        return "Generic";
    }

    @Override
    public <T extends Throwable> void accept(LanguageInformationVisitor<T> visitor) throws T {
        visitor.visit(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o != null && getClass() == o.getClass();
    }
}
