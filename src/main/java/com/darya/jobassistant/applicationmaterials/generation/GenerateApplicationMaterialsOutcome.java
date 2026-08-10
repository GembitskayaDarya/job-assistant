package com.darya.jobassistant.applicationmaterials.generation;

import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGeneration;
import com.darya.jobassistant.applicationmaterials.result.aggregate.ApplicationMaterialGenerationResult;

/**
 * Sprint 10 Step 3: {@link GenerateApplicationMaterialsUseCase#generate}'s return value - the
 * freshest known {@link #generation} state plus, when one exists, the {@link #result}. {@link
 * #result} is non-null exactly for {@link GenerationOutcomeStatus#COMPLETED}/{@link
 * GenerationOutcomeStatus#ALREADY_COMPLETED}.
 */
public record GenerateApplicationMaterialsOutcome(
        GenerationOutcomeStatus status,
        ApplicationMaterialGeneration generation,
        ApplicationMaterialGenerationResult result
) {

    public GenerateApplicationMaterialsOutcome {
        if (status == null) {
            throw new IllegalArgumentException("Generate application materials outcome status must not be null");
        }
        if (generation == null) {
            throw new IllegalArgumentException("Generate application materials outcome generation must not be null");
        }
    }
}
