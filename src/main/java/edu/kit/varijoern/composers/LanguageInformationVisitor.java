package edu.kit.varijoern.composers;

/**
 * Visits {@link LanguageInformation} instances. Unimplemented subclasses will print a warning.
 */
public abstract class LanguageInformationVisitor {
    private void visitUnimplemented(LanguageInformation languageInformation) {
        System.err.printf(
                "Language %s is not supported by the analyzer%n",
                languageInformation.getName()
        );
    }

    /**
     * Visits a {@link CCPPLanguageInformation} instance.
     *
     * @param languageInformation the instance to visit
     */
    public void visit(CCPPLanguageInformation languageInformation) {
        visitUnimplemented(languageInformation);
    }

    /**
     * Visits a {@link GenericLanguageInformation} instance.
     *
     * @param languageInformation the instance to visit
     */
    public void visit(GenericLanguageInformation languageInformation) {
        visitUnimplemented(languageInformation);
    }
}
