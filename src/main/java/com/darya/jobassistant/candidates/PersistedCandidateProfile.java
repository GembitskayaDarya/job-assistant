package com.darya.jobassistant.candidates;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Framework-free domain model for the Candidate Profile aggregate persisted by Sprint 9 Step 1's
 * PostgreSQL schema (parent {@code candidate_profile} row plus its {@code candidate_profile_skill}
 * and {@code candidate_profile_language} rows) - the type {@link CandidateProfileRepositoryPort}
 * loads and saves.
 *
 * <p>This is deliberately a different type from {@link CandidateProfile}: that record's rich,
 * importance-weighted {@link CandidatePreferences} has no analog in Step 1's flat schema
 * ({@code preferredLocation}, structured {@code salaryCurrency}/{@code minimumSalary}, etc.), and
 * {@link CandidateProfile}/{@link CandidateSkill} have dozens of existing call sites across
 * unrelated features that a shape change would break. This mirrors the same split this codebase
 * already uses for {@code ai.model.JobAnalysis} (ephemeral AI value) vs. {@code
 * ai.model.PersistedJobAnalysis} (persisted wrapper).
 *
 * <p>{@link #id} is {@code null} for a profile not yet persisted; {@link #version} is the
 * optimistic-locking token a caller must supply unchanged from a prior load to update that exact
 * revision - see {@link CandidateProfileRepositoryPort#save}.
 */
public record PersistedCandidateProfile(
        UUID id,
        String profileKey,
        String targetRole,
        String seniority,
        int experienceYears,
        String preferredCompanyType,
        String preferredLocation,
        String employmentModel,
        String remotePolicy,
        String salaryCurrency,
        BigDecimal minimumSalary,
        List<PersistedCandidateSkill> skills,
        List<PersistedCandidateLanguage> languages,
        long version
) {
    public PersistedCandidateProfile {
        profileKey = profileKey == null ? null : profileKey.trim();
        if (profileKey == null || profileKey.isEmpty()) {
            throw new IllegalArgumentException("Candidate profile key must not be null or blank");
        }
        targetRole = targetRole == null ? null : targetRole.trim();
        if (targetRole == null || targetRole.isEmpty()) {
            throw new IllegalArgumentException("Candidate target role must not be null or blank");
        }
        if (experienceYears < 0) {
            throw new IllegalArgumentException("Candidate experience years must not be negative");
        }
        if (minimumSalary != null && minimumSalary.signum() < 0) {
            throw new IllegalArgumentException("Candidate minimum salary must not be negative");
        }
        if (version < 0) {
            throw new IllegalArgumentException("Candidate profile version must not be negative");
        }
        skills = skills == null ? List.of() : List.copyOf(skills);
        languages = languages == null ? List.of() : List.copyOf(languages);
        requireUniqueSkillNames(skills);
        requireUniqueLanguageCodes(languages);
    }

    private static void requireUniqueSkillNames(List<PersistedCandidateSkill> skills) {
        Set<String> seen = new LinkedHashSet<>();
        for (PersistedCandidateSkill skill : skills) {
            if (!seen.add(skill.name())) {
                throw new IllegalArgumentException("Duplicate skill name in candidate profile: " + skill.name());
            }
        }
    }

    private static void requireUniqueLanguageCodes(List<PersistedCandidateLanguage> languages) {
        Set<String> seen = new LinkedHashSet<>();
        for (PersistedCandidateLanguage language : languages) {
            if (!seen.add(language.languageCode())) {
                throw new IllegalArgumentException("Duplicate language code in candidate profile: " + language.languageCode());
            }
        }
    }
}
