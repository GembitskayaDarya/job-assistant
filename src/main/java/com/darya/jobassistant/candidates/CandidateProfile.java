package com.darya.jobassistant.candidates;

import java.util.List;

public record CandidateProfile(
        String targetRole,
        String targetSeniority,
        List<CandidateSkill> skills,
        List<String> languages,
        int experienceYears,
        CandidatePreferences preferences
) {
    public CandidateProfile {
        targetRole = targetRole == null ? null : targetRole.trim();
        if (targetRole == null || targetRole.isEmpty()) {
            throw new IllegalArgumentException("Candidate target role must not be null or blank");
        }
        targetSeniority = targetSeniority == null ? null : targetSeniority.trim();
        if (targetSeniority == null || targetSeniority.isEmpty()) {
            throw new IllegalArgumentException("Candidate target seniority must not be null or blank");
        }
        if (experienceYears < 0) {
            throw new IllegalArgumentException("Candidate experience years must not be negative");
        }
        if (preferences == null) {
            throw new IllegalArgumentException("Candidate preferences must not be null");
        }
        skills = skills == null ? List.of() : List.copyOf(skills);
        languages = languages == null ? List.of() : List.copyOf(languages);
    }
}
