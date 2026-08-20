package com.darya.jobassistant.applicationmaterials.generation.model;

/**
 * Sprint 10 Step 3 (Sprint 11 Big Block 7 correction): {@link ApplicationMaterialsAiPort#generate}'s
 * complete return value - the raw (not-yet-validated) {@link GeneratedCoverLetter} plus the small,
 * safe AI/prompt provenance metadata {@code ApplicationMaterialGenerationResult} needs at
 * persistence time. Deliberately not folded into {@link GeneratedCoverLetter} itself: {@link
 * #aiProvider}/{@link #aiModel}/{@link #promptVersion} are facts about how the content was produced,
 * not part of the document's own semantic content.
 *
 * <p>This port/response now covers cover-letter generation only - CV content comes from the Sprint
 * 11 {@code CvTailoringUseCase} pipeline instead (see {@code ApplicationMaterialsAiPort}'s javadoc).
 */
public record ApplicationMaterialsGenerationResponse(
        GeneratedCoverLetter coverLetter,
        String aiProvider,
        String aiModel,
        int promptVersion
) {

    public ApplicationMaterialsGenerationResponse {
        if (coverLetter == null) {
            throw new IllegalArgumentException("Application materials generation response coverLetter must not be null");
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
