package com.darya.jobassistant.applicationmaterials.generation.model;

/**
 * Sprint 10 Step 3: {@link ApplicationMaterialsAiPort#generate}'s complete return value - the raw
 * (not-yet-validated) {@link GeneratedApplicationMaterials} plus the small, safe AI/prompt
 * provenance metadata {@code ApplicationMaterialGenerationResult} needs at persistence time.
 * Deliberately not folded into {@link GeneratedApplicationMaterials} itself: {@link #aiProvider}/
 * {@link #aiModel}/{@link #promptVersion} are facts about how the content was produced, not part
 * of the document's own semantic content.
 */
public record ApplicationMaterialsGenerationResponse(
        GeneratedApplicationMaterials materials,
        String aiProvider,
        String aiModel,
        int promptVersion
) {

    public ApplicationMaterialsGenerationResponse {
        if (materials == null) {
            throw new IllegalArgumentException("Application materials generation response materials must not be null");
        }
        if (aiProvider == null || aiProvider.isBlank()) {
            throw new IllegalArgumentException("Application materials generation response aiProvider must not be blank");
        }
        if (aiModel == null || aiModel.isBlank()) {
            throw new IllegalArgumentException("Application materials generation response aiModel must not be blank");
        }
        if (promptVersion <= 0) {
            throw new IllegalArgumentException("Application materials generation response promptVersion must be positive");
        }
    }
}
