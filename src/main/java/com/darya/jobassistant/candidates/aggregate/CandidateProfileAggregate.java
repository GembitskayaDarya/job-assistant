package com.darya.jobassistant.candidates.aggregate;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Framework-free domain model for the Candidate Profile business aggregate backed by Sprint 9
 * Step 1's PostgreSQL schema (parent {@code candidate_profile} row plus its {@code
 * candidate_profile_skill} and {@code candidate_profile_language} rows) - the type {@link
 * CandidateProfileRepositoryPort} loads and saves.
 *
 * <h2>Intended architectural role</h2>
 *
 * <p>This is the intended <b>future source-of-truth</b> business aggregate for Candidate Profile
 * data, not an infrastructure DTO - persistence is an implementation detail of what this type
 * represents, not its purpose. It lives in its own {@code candidates.aggregate} package,
 * deliberately separate from the flat top-level {@code candidates} package, to make that
 * distinction visible in the code layout itself:
 *
 * <pre>{@code
 * CandidateProfileAggregate
 *     = future PostgreSQL-backed source-of-truth domain aggregate (this type)
 *
 * com.darya.jobassistant.candidates.CandidateProfile
 *     = current analysis-oriented projection JobAnalysisService actually reads from today,
 *       sourced from config/candidate-profile.yml via ConfigurationCandidateProfileProvider
 *
 * future assembler (not implemented yet)
 *     CandidateProfileAggregate -> CandidateProfile
 * }</pre>
 *
 * <p>{@code CandidateProfile}'s rich, importance-weighted {@code CandidatePreferences} has no
 * equivalent in this aggregate's flat scalar shape ({@code preferredLocation}, structured {@code
 * salaryCurrency}/{@code minimumSalary}, etc.), and {@code CandidateProfile}/{@code CandidateSkill}
 * (the YAML-oriented ones) have dozens of existing call sites across unrelated features that a
 * shape change would break - so the two are intentionally not merged in this step, and this
 * aggregate does not persist weighted preferences until a proper schema and mapping for them is
 * designed. Building the assembler above, and switching {@code JobAnalysisService} to consume it,
 * is later Sprint 9 work.
 *
 * <p>{@link #id} is {@code null} for a profile not yet persisted; {@link #version} is the
 * optimistic-locking token a caller must supply unchanged from a prior load to update that exact
 * revision - see {@link CandidateProfileRepositoryPort#save}. Every save of an existing aggregate,
 * including one that only changes {@link #skills} or {@link #languages}, performs a
 * version-checked write of the whole aggregate and increments {@link #version} - skills and
 * languages are part of this aggregate, not independently versioned sub-resources.
 */
public record CandidateProfileAggregate(
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
        List<CandidateSkill> skills,
        List<CandidateLanguage> languages,
        long version
) {
    public CandidateProfileAggregate {
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

    private static void requireUniqueSkillNames(List<CandidateSkill> skills) {
        Set<String> seen = new LinkedHashSet<>();
        for (CandidateSkill skill : skills) {
            if (!seen.add(skill.name())) {
                throw new IllegalArgumentException("Duplicate skill name in candidate profile: " + skill.name());
            }
        }
    }

    private static void requireUniqueLanguageCodes(List<CandidateLanguage> languages) {
        Set<String> seen = new LinkedHashSet<>();
        for (CandidateLanguage language : languages) {
            if (!seen.add(language.languageCode())) {
                throw new IllegalArgumentException("Duplicate language code in candidate profile: " + language.languageCode());
            }
        }
    }
}
