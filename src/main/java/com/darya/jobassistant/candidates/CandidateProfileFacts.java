package com.darya.jobassistant.candidates;

import java.util.List;

/**
 * Sprint 11 Step 1 correction: the complete, framework-free, lossless candidate-profile facts - the
 * type {@link com.darya.jobassistant.candidatecontext.CandidateContextSnapshot} carries, produced by
 * {@code candidates.migration.CandidateProfileFactsAssembler} directly from {@code
 * CandidateProfileAggregate} with nothing dropped (unlike {@link CandidateProfile}, which is
 * intentionally bounded for vacancy-analysis prompt rendering and drops {@link
 * CandidateSkillFacts#category} and language proficiency).
 *
 * <p>This is the "full framework-free candidate profile snapshot" stage of the pipeline:
 *
 * <pre>{@code
 * CandidateProfileAggregate -> CandidateProfileFacts -> CandidateContextSnapshot -> use-case projections
 * }</pre>
 *
 * Lossy narrowing for a specific use case (e.g. {@code
 * candidates.migration.CandidateProfileAnalysisAssembler#toAnalysisProfile(CandidateProfileFacts)}
 * producing the bounded {@link CandidateProfile} vacancy-analysis needs) happens explicitly at that
 * use case's own boundary, never here - this type itself must never become constrained by what any
 * single downstream use case (analysis, application materials, CV generation) happens to need.
 *
 * <p>{@link #preferences} reuses {@link CandidatePreferences} unchanged: that mapping from {@code
 * CandidateProfileAggregate} was already lossless (see {@code
 * CandidateProfileAnalysisAssemblerTest#toAnalysisProfile_weightedPreferencesAreReconstructedCorrectly}),
 * so there is nothing to preserve further for preferences specifically.
 */
public record CandidateProfileFacts(
        String targetRole,
        String targetSeniority,
        List<CandidateSkillFacts> skills,
        List<CandidateLanguageFacts> languages,
        int experienceYears,
        CandidatePreferences preferences
) {

    public CandidateProfileFacts {
        targetRole = targetRole == null ? null : targetRole.trim();
        if (targetRole == null || targetRole.isEmpty()) {
            throw new IllegalArgumentException("Candidate profile facts target role must not be null or blank");
        }
        targetSeniority = targetSeniority == null ? null : targetSeniority.trim();
        if (targetSeniority == null || targetSeniority.isEmpty()) {
            throw new IllegalArgumentException("Candidate profile facts target seniority must not be null or blank");
        }
        if (experienceYears < 0) {
            throw new IllegalArgumentException("Candidate profile facts experience years must not be negative");
        }
        if (preferences == null) {
            throw new IllegalArgumentException("Candidate profile facts preferences must not be null");
        }
        skills = skills == null ? List.of() : List.copyOf(skills);
        languages = languages == null ? List.of() : List.copyOf(languages);
    }
}
