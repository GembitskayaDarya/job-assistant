package com.darya.jobassistant.applicationmaterials.render.ats;

/** Sprint 11 Big Block 7: thrown by {@link AtsPdfTextExtractorPort#extractText} when the given bytes cannot be parsed as a PDF. */
public class AtsTextExtractionException extends RuntimeException {

    public AtsTextExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
