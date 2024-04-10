package edu.kit.varijoern.composers;

/**
 * Derived classes contain information about how to handle a specific language in a composition.
 */
public abstract class LanguageInformation {
    /**
     * Returns the name of the language.
     *
     * @return the name of the language
     */
    public abstract String getName();

    /**
     * Accepts a {@link LanguageInformationVisitor}.
     *
     * @param visitor the visitor to accept
     */
    public abstract void accept(LanguageInformationVisitor visitor);
}
