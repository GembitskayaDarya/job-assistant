package com.darya.jobassistant.candidates;

import java.util.List;

/**
 * <p>{@link #fullName}/{@link #email}/{@link #phone}/{@link #linkedinUrl}/{@link #cvLocation}/
 * {@link #cvHeadline} and {@link #education} (Sprint 11 Step 5) are fixed factual CV-presentation
 * data - unlike every other field here, {@code JobAnalysisService}'s vacancy-matching prompt never
 * reads them. They ride on this same YAML-import type (rather than a second, parallel import
 * pipeline) because {@code CandidateProfileMigrationUseCase}'s existing fingerprint/parity-checking
 * machinery is the one mechanism in this codebase that protects candidate-profile data integrity
 * end to end; forking a separate pipeline for these facts would duplicate that machinery for no
 * benefit. {@link #cvHeadline} is deliberately not derived from {@link #targetRole} - see {@code
 * candidates.aggregate.CandidateProfileAggregate}'s javadoc for why the two must stay independent.
 *
 * <p>{@link #languages} (acceptance correction) carries {@link CandidateLanguageEntry} - name plus
 * proficiency - not a plain {@code List<String>}: the old shape had no way to express proficiency
 * at all, so it was always imported as {@code null}. {@code JobAnalysisService}'s prompt still only
 * needs the language name, extracted explicitly at that call site.
 */
public record CandidateProfile(
        String targetRole,
        String targetSeniority,
        List<CandidateSkill> skills,
        List<CandidateLanguageEntry> languages,
        int experienceYears,
        CandidatePreferences preferences,
        String fullName,
        String email,
        String phone,
        String linkedinUrl,
        String cvLocation,
        String cvHeadline,
        List<CandidateEducationEntry> education
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
        fullName = trimToNull(fullName);
        email = trimToNull(email);
        phone = trimToNull(phone);
        linkedinUrl = trimToNull(linkedinUrl);
        cvLocation = trimToNull(cvLocation);
        cvHeadline = trimToNull(cvHeadline);
        education = education == null ? List.of() : List.copyOf(education);
    }

    /**
     * Convenience constructor matching this type's pre-Sprint-11-Step-5 shape - defaults the new
     * CV header/contact facts to {@code null} and {@link #education} to empty, so existing call
     * sites (production and test) keep compiling unchanged.
     */
    public CandidateProfile(
            String targetRole, String targetSeniority, List<CandidateSkill> skills, List<CandidateLanguageEntry> languages,
            int experienceYears, CandidatePreferences preferences) {
        this(targetRole, targetSeniority, skills, languages, experienceYears, preferences,
                null, null, null, null, null, null, List.of());
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
