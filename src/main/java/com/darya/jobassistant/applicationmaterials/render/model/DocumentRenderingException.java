package com.darya.jobassistant.applicationmaterials.render.model;

/**
 * Provider-neutral document-rendering failure. Never carries a PDFBox (or other rendering-library)
 * exception type, raw internal detail, or file paths - only a safe message plus (where one exists)
 * the original cause, matching {@code ApplicationMaterialsAiException}'s convention.
 */
public class DocumentRenderingException extends RuntimeException {

    public DocumentRenderingException(String message) {
        super(message);
    }

    public DocumentRenderingException(String message, Throwable cause) {
        super(message, cause);
    }
}
