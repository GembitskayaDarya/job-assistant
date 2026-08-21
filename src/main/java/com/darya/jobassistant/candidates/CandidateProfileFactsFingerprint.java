package com.darya.jobassistant.candidates;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * Sprint 11 production hardening: a deterministic SHA-256 fingerprint of a {@link
 * CandidateProfileFacts}' complete semantic content - the framework-free, generation-facing
 * counterpart of {@code candidates.migration.CandidateProfileFingerprint} (which fingerprints the
 * persistence-layer {@code CandidateProfileAggregate} instead). Exists specifically so {@code
 * applicationmaterials.preparation.ApplicationMaterialSourceFingerprint} can fingerprint exactly the
 * same immutable snapshot {@code CvTailoringUseCase}/cover-letter generation already consume - never
 * a second, independently-loaded representation of candidate data.
 *
 * <p>Uses an explicit, hand-built canonical string - never {@code toString()}/Java serialization.
 * {@link #sha256} is a pure function of {@code facts}' own field values; no id, version, or
 * timestamp is part of any type in {@link CandidateProfileFacts}' graph, so there is nothing to
 * exclude the way {@code CandidateProfileFingerprint} explicitly excludes persistence identity.
 *
 * <h2>Order semantics (deliberately different from {@code CandidateProfileFingerprint})</h2>
 *
 * {@link #appendSkills} hashes {@link CandidateProfileFacts#skills()} <em>positionally</em> (list
 * order, never re-sorted) - unlike the audit-only {@code CandidateProfileFingerprint}, which sorts
 * skills by name because skill order carries no meaning for its "is this the same profile content"
 * purpose. Here, list order <em>is</em> generation-relevant: {@code CvAssembler#assembleBaseline}
 * copies {@link CandidateProfileFacts#skills()} straight into the baseline CV's skill order, and the
 * same list order is what {@code CvTailoringReferenceIndex} assigns typed reference tokens from for
 * AI tailoring. A skill reorder therefore must change this fingerprint even though it would not
 * change {@code CandidateProfileFingerprint}. {@link #appendLanguages} is positional for the same
 * reason ({@link CandidateLanguageFacts} carries no explicit {@code displayOrder} field - list
 * position is the only order signal that exists, and it is presentation-significant). {@link
 * #appendEducation} sorts by {@link CandidateEducationFacts#displayOrder()} explicitly (an explicit
 * field exists, so canonicalizing by it - rather than trusting incoming list order - is both
 * possible and safer against future loader changes), including that value in each entry's hashed
 * content so a genuine reorder still changes the fingerprint.
 */
public final class CandidateProfileFactsFingerprint {

    /** A control character with no plausible legitimate appearance in a candidate profile field. */
    private static final char FIELD_SEPARATOR = '';

    private CandidateProfileFactsFingerprint() {
    }

    public static String sha256(CandidateProfileFacts facts) {
        return HexFormat.of().formatHex(digest(canonicalize(facts)));
    }

    private static String canonicalize(CandidateProfileFacts facts) {
        StringBuilder canonical = new StringBuilder();
        field(canonical, facts.targetRole());
        field(canonical, facts.targetSeniority());
        field(canonical, String.valueOf(facts.experienceYears()));
        field(canonical, facts.fullName());
        field(canonical, facts.email());
        field(canonical, facts.phone());
        field(canonical, facts.linkedinUrl());
        field(canonical, facts.cvLocation());
        field(canonical, facts.cvHeadline());
        appendSkills(canonical, facts.skills());
        appendLanguages(canonical, facts.languages());
        appendEducation(canonical, facts.education());
        appendPreferences(canonical, facts.preferences());
        return canonical.toString();
    }

    private static void appendSkills(StringBuilder canonical, List<CandidateSkillFacts> skills) {
        for (CandidateSkillFacts skill : skills) {
            field(canonical, skill.name());
            field(canonical, skill.category());
            field(canonical, skill.proficiency() == null ? null : skill.proficiency().name());
            field(canonical, skill.note());
        }
    }

    private static void appendLanguages(StringBuilder canonical, List<CandidateLanguageFacts> languages) {
        for (CandidateLanguageFacts language : languages) {
            field(canonical, language.name());
            field(canonical, language.proficiency());
        }
    }

    private static void appendEducation(StringBuilder canonical, List<CandidateEducationFacts> education) {
        education.stream()
                .sorted(Comparator.comparingInt(CandidateEducationFacts::displayOrder))
                .forEach(entry -> {
                    field(canonical, entry.institution());
                    field(canonical, entry.degree());
                    field(canonical, entry.fieldOfStudy());
                    field(canonical, entry.location());
                    field(canonical, String.valueOf(entry.startDate()));
                    field(canonical, String.valueOf(entry.endDate()));
                    field(canonical, entry.description());
                    field(canonical, String.valueOf(entry.displayOrder()));
                });
    }

    private static void appendPreferences(StringBuilder canonical, CandidatePreferences preferences) {
        field(canonical, preferences.currentCountry());
        field(canonical, preferences.preferredWorkArrangement());
        field(canonical, preferences.workArrangementImportance() == null ? null : preferences.workArrangementImportance().name());
        field(canonical, String.join(",", preferences.allowedWorkCountries()));
        field(canonical, String.valueOf(preferences.relocationAllowed()));
        field(canonical, String.join(",", preferences.preferredContractTypes()));
        field(canonical, preferences.contractTypeImportance() == null ? null : preferences.contractTypeImportance().name());
        field(canonical, preferences.preferredCompanyType());
        field(canonical, preferences.companyTypeImportance() == null ? null : preferences.companyTypeImportance().name());
        field(canonical, preferences.salaryExpectation());
    }

    private static void field(StringBuilder canonical, String value) {
        canonical.append(value == null ? "" : value).append(FIELD_SEPARATOR);
    }

    private static byte[] digest(String canonical) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be available on every JVM", e);
        }
    }
}
