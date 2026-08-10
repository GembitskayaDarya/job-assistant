package com.darya.jobassistant.candidatecontext.applicationmaterials;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sprint 10 Step 2: bounds for {@link CandidateContextForApplicationMaterialsSelector} - mirrors
 * {@code candidatecontext.analysis.CandidateContextAnalysisProperties}'s convention exactly (a
 * flat record, manually range-validated in the compact constructor, each field with a generous
 * defensive upper bound of its own, {@link #maxTotalCharacters()} the real backstop). Always
 * eagerly bound and validated: normal runtime must always have a usable set of limits, defaulted
 * in {@code application.yml} so no override is required.
 *
 * <p>Deliberately a separate properties type and configuration prefix from {@code
 * CandidateContextAnalysisProperties} rather than a shared one: a tailored CV/cover letter
 * legitimately draws on more career evidence than a one-shot fit-analysis prompt, so the two
 * features' bounds are expected to diverge over time (see the differing defaults in {@code
 * application.yml}).
 *
 * <p>No separate "max companies" bound: the number of distinct companies represented can never
 * exceed {@link #maxPositions()} (at least one selected position is required for a company to
 * appear at all - see {@code SelectedCareerCompany}), so a dedicated company cap would only
 * duplicate an already-enforced limit.
 */
@ConfigurationProperties(prefix = "candidate-context.application-materials")
public record CandidateContextForApplicationMaterialsProperties(
        int maxPositions,
        int maxProjects,
        int maxPositionResponsibilities,
        int maxPositionAchievements,
        int maxProjectResponsibilities,
        int maxProjectAchievements,
        int maxTechnologiesPerProject,
        int maxFieldCharacters,
        int maxTotalCharacters
) {

    private static final int MAX_POSITIONS_UPPER_BOUND = 50;
    private static final int MAX_PROJECTS_UPPER_BOUND = 100;
    private static final int MAX_BULLETS_UPPER_BOUND = 50;
    private static final int MAX_TECHNOLOGIES_UPPER_BOUND = 100;
    private static final int MAX_FIELD_CHARACTERS_UPPER_BOUND = 20_000;
    private static final int MAX_TOTAL_CHARACTERS_UPPER_BOUND = 200_000;

    public CandidateContextForApplicationMaterialsProperties {
        requireInRange(maxPositions, 1, MAX_POSITIONS_UPPER_BOUND, "max-positions");
        requireInRange(maxProjects, 1, MAX_PROJECTS_UPPER_BOUND, "max-projects");
        requireInRange(maxPositionResponsibilities, 1, MAX_BULLETS_UPPER_BOUND, "max-position-responsibilities");
        requireInRange(maxPositionAchievements, 1, MAX_BULLETS_UPPER_BOUND, "max-position-achievements");
        requireInRange(maxProjectResponsibilities, 1, MAX_BULLETS_UPPER_BOUND, "max-project-responsibilities");
        requireInRange(maxProjectAchievements, 1, MAX_BULLETS_UPPER_BOUND, "max-project-achievements");
        requireInRange(maxTechnologiesPerProject, 1, MAX_TECHNOLOGIES_UPPER_BOUND, "max-technologies-per-project");
        requireInRange(maxFieldCharacters, 1, MAX_FIELD_CHARACTERS_UPPER_BOUND, "max-field-characters");
        requireInRange(maxTotalCharacters, 1, MAX_TOTAL_CHARACTERS_UPPER_BOUND, "max-total-characters");
        if (maxTotalCharacters < maxFieldCharacters) {
            throw new IllegalArgumentException(
                    "candidate-context.application-materials.max-total-characters (" + maxTotalCharacters
                            + ") must be at least candidate-context.application-materials.max-field-characters ("
                            + maxFieldCharacters + ")");
        }
    }

    private static void requireInRange(int value, int min, int max, String propertyName) {
        if (value < min || value > max) {
            throw new IllegalArgumentException("candidate-context.application-materials." + propertyName
                    + " must be between " + min + " and " + max + ", but was " + value);
        }
    }
}
