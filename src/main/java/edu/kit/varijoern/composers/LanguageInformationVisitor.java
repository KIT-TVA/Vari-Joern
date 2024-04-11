package edu.kit.varijoern.composers;

/**
 * Visits {@link LanguageInformation} instances.
 *
 * @param <T> the type of exception that can be thrown by the visitor
 */
public abstract class LanguageInformationVisitor<T extends Throwable> {
    /**
     * The default behavior for unimplemented subclasses of {@link LanguageInformation}.
     *
     * @param languageInformation the instance to visit whose subclass has no specific implementation
     */
    protected abstract void visitUnimplemented(LanguageInformation languageInformation) throws T;

    /**
     * Visits a {@link CCPPLanguageInformation} instance.
     *
     * @param languageInformation the instance to visit
     */
    public void visit(CCPPLanguageInformation languageInformation) throws T {
        visitUnimplemented(languageInformation);
    }

    /**
     * Visits a {@link GenericLanguageInformation} instance.
     *
     * @param languageInformation the instance to visit
     */
    public void visit(GenericLanguageInformation languageInformation) throws T {
        visitUnimplemented(languageInformation);
    }
}
