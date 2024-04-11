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
     * @param <T>     the type of exception that can be thrown by the visitor
     */
    public abstract <T extends Throwable> void accept(LanguageInformationVisitor<T> visitor) throws T;
}
