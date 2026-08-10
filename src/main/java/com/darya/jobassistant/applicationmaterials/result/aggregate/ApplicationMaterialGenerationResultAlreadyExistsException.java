package com.darya.jobassistant.applicationmaterials.result.aggregate;

import java.util.UUID;

/**
 * Thrown by {@link ApplicationMaterialGenerationResultRepositoryPort#save} when a result already
 * exists for the given generation - {@code
 * uk_application_material_generation_result_generation_id} (V22) is the actual enforcement; this
 * translates that database constraint into a framework-free signal, matching {@code
 * CareerHistoryAlreadyExistsException}'s convention, so a caller never silently creates a second
 * result for an already-completed generation.
 */
public class ApplicationMaterialGenerationResultAlreadyExistsException extends RuntimeException {

    public ApplicationMaterialGenerationResultAlreadyExistsException(UUID generationId) {
        super("An application material generation result already exists for generationId '" + generationId + "'");
    }
}
