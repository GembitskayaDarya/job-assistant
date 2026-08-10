package com.darya.jobassistant.applicationmaterials.aggregate;

import java.util.UUID;

/**
 * Sprint 10 Step 3: thrown when a caller (e.g. {@code GenerateApplicationMaterialsUseCase})
 * requests an {@link ApplicationMaterialGeneration} by id and no such row exists. Carries only the
 * requested id - never any generation content.
 */
public class ApplicationMaterialGenerationNotFoundException extends RuntimeException {

    public ApplicationMaterialGenerationNotFoundException(UUID generationId) {
        super("No application material generation exists for id '" + generationId + "'");
    }
}
