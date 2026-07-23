package com.darya.jobassistant.candidates;

import java.util.List;

public record CandidateProfile(
        String targetRole,
        List<String> skills,
        List<String> languages,
        int experienceYears,
        String preferredCompanyType,
        String preferredLocation
) {
    public CandidateProfile {
        skills = skills == null ? List.of() : List.copyOf(skills);
        languages = languages == null ? List.of() : List.copyOf(languages);
    }
}
