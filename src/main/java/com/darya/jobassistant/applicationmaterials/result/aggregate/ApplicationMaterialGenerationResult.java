package com.darya.jobassistant.applicationmaterials.result.aggregate;

import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCoverLetter;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCv;
import java.time.Instant;
import java.util.UUID;

/**
 * Sprint 10 Step 3: framework-free domain aggregate for the structured semantic CV/cover-letter
 * content produced by one {@code ApplicationMaterialGeneration} attempt - backed by V22's {@code
 * application_material_generation_result} table. A generation has at most one result (enforced by
 * {@code uk_application_material_generation_result_generation_id}); the result belongs to the
 * generation, and is never itself updated after creation - see {@link #create}.
 *
 * <p>{@link #cv}/{@link #coverLetter} always hold the already-validated, canonically-resolved
 * content ({@code GeneratedApplicationMaterialsValidator}'s output) - never a raw, unvalidated AI
 * response.
 */
public record ApplicationMaterialGenerationResult(
        UUID id,
        UUID generationId,
        int schemaVersion,
        GeneratedCv cv,
        GeneratedCoverLetter coverLetter,
        String aiProvider,
        String aiModel,
        int promptVersion,
        Instant generatedAt,
        Instant createdAt
) {

    /**
     * The current structured-content schema version every newly created result is stamped with -
     * see {@link #create}. Bumped only if {@link GeneratedCv}/{@link GeneratedCoverLetter}'s shape
     * changes in a way a reader of already-persisted JSONB content needs to distinguish.
     */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public ApplicationMaterialGenerationResult {
        if (generationId == null) {
            throw new IllegalArgumentException("Application material generation result generationId must not be null");
        }
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("Application material generation result schemaVersion must be positive");
        }
        if (cv == null) {
            throw new IllegalArgumentException("Application material generation result cv must not be null");
        }
        if (coverLetter == null) {
            throw new IllegalArgumentException("Application material generation result coverLetter must not be null");
        }
        if (aiProvider == null || aiProvider.isBlank()) {
            throw new IllegalArgumentException("Application material generation result aiProvider must not be blank");
        }
        if (aiModel == null || aiModel.isBlank()) {
            throw new IllegalArgumentException("Application material generation result aiModel must not be blank");
        }
        if (promptVersion <= 0) {
            throw new IllegalArgumentException("Application material generation result promptVersion must be positive");
        }
        if (generatedAt == null) {
            throw new IllegalArgumentException("Application material generation result generatedAt must not be null");
        }
    }

    /**
     * Creates a newly produced result at {@link #CURRENT_SCHEMA_VERSION}, not yet persisted ({@link
     * #id}/{@link #createdAt} are {@code null}) - the only domain-safe way to build one for {@link
     * ApplicationMaterialGenerationResultRepositoryPort#save}.
     */
    public static ApplicationMaterialGenerationResult create(
            UUID generationId, GeneratedCv cv, GeneratedCoverLetter coverLetter,
            String aiProvider, String aiModel, int promptVersion, Instant generatedAt) {
        return new ApplicationMaterialGenerationResult(
                null, generationId, CURRENT_SCHEMA_VERSION, cv, coverLetter, aiProvider, aiModel, promptVersion, generatedAt, null);
    }
}
