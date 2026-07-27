package com.darya.jobassistant.ai.model;

import java.util.List;

public record JobAnalysis(
        int score,
        List<String> pros,
        List<String> cons,
        List<String> missingRequiredSkills,
        List<String> missingPreferredSkills,
        String experienceAssessment,
        String preferencesAssessment,
        String summary
) {
    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 100;

    public JobAnalysis {
        if (score < MIN_SCORE || score > MAX_SCORE) {
            throw new IllegalArgumentException(
                    "score must be between %d and %d, but was %d".formatted(MIN_SCORE, MAX_SCORE, score));
        }
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        if (experienceAssessment == null || experienceAssessment.isBlank()) {
            throw new IllegalArgumentException("experienceAssessment must not be blank");
        }
        if (preferencesAssessment == null || preferencesAssessment.isBlank()) {
            throw new IllegalArgumentException("preferencesAssessment must not be blank");
        }
        pros = pros == null ? List.of() : List.copyOf(pros);
        cons = cons == null ? List.of() : List.copyOf(cons);
        missingRequiredSkills = missingRequiredSkills == null ? List.of() : List.copyOf(missingRequiredSkills);
        missingPreferredSkills = missingPreferredSkills == null ? List.of() : List.copyOf(missingPreferredSkills);
    }
}
