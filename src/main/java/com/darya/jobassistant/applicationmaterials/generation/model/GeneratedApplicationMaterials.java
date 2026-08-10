package com.darya.jobassistant.applicationmaterials.generation.model;

/**
 * Sprint 10 Step 3: the complete structured semantic content produced for one tailored application
 * - a CV and a cover letter, both framework-free and free of any presentation/layout concern.
 *
 * <p>This exact type is used twice in the generation flow, in two different trust states:
 * {@link ApplicationMaterialsAiPort#generate} returns it freshly mapped from the AI response
 * (mechanically parsed, not yet semantically checked), and {@code
 * GeneratedApplicationMaterialsValidator#validate} returns a second instance of it - the same
 * shape, but with every skill's {@link GeneratedCvSkill#proficiency()} canonically resolved and
 * every provenance reference confirmed to exist in the exact bounded context used for this
 * generation. Only the validator's output is ever persisted.
 */
public record GeneratedApplicationMaterials(GeneratedCv cv, GeneratedCoverLetter coverLetter) {

    public GeneratedApplicationMaterials {
        if (cv == null) {
            throw new IllegalArgumentException("Generated application materials cv must not be null");
        }
        if (coverLetter == null) {
            throw new IllegalArgumentException("Generated application materials coverLetter must not be null");
        }
    }
}
