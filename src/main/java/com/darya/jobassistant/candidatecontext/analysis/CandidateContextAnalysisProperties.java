package com.darya.jobassistant.candidatecontext.analysis;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sprint 9 Step 8: bounds for {@link CandidateContextForAnalysisSelector} - the deterministic
 * limits that keep Career History evidence sent to the AI provider bounded, no matter how large
 * the underlying {@code CareerHistoryAggregate} grows. Always eagerly bound and validated (unlike
 * {@code CandidateProfileProperties}'s optional YAML import): normal runtime must always have a
 * usable set of limits, defaulted in {@code application.yml} so no override is required.
 *
 * <p>Mirrors {@code JobSearchQueryPlanningProperties}'s convention: a flat record, manually
 * range-validated in the compact constructor rather than via jakarta annotations, each with a
 * generous defensive upper bound of its own (never meant to be reached in practice - {@link
 * #maxTotalCharacters()} is the real backstop).
 */
@ConfigurationProperties(prefix = "candidate-context.analysis")
public record CandidateContextAnalysisProperties(
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

    public CandidateContextAnalysisProperties {
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
                    "candidate-context.analysis.max-total-characters (" + maxTotalCharacters
                            + ") must be at least candidate-context.analysis.max-field-characters (" + maxFieldCharacters + ")");
        }
    }

    private static void requireInRange(int value, int min, int max, String propertyName) {
        if (value < min || value > max) {
            throw new IllegalArgumentException("candidate-context.analysis." + propertyName
                    + " must be between " + min + " and " + max + ", but was " + value);
        }
    }
}
