package com.darya.jobassistant.applicationmaterials.generation.model;

/**
 * Thrown by {@code GeneratedApplicationMaterialsValidator#validate} when the AI response fails any
 * deterministic structural or provenance check - an invalid/unknown source UUID, a bullet with no
 * source reference, a skill not owned by the candidate, an unsupported enum value, or an
 * output-size limit exceeded. The message is safe to surface in {@code
 * ApplicationMaterialGeneration#failureMessage} (bounded, never embeds a stack trace or secret),
 * but is deliberately capped by the caller before persistence in case it grows unexpectedly large.
 */
public class ApplicationMaterialsValidationException extends RuntimeException {

    public ApplicationMaterialsValidationException(String message) {
        super(message);
    }
}
