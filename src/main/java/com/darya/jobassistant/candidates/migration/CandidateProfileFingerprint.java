package com.darya.jobassistant.candidates.migration;

import com.darya.jobassistant.candidates.aggregate.CandidateEducation;
import com.darya.jobassistant.candidates.aggregate.CandidateLanguage;
import com.darya.jobassistant.candidates.aggregate.CandidatePreferenceType;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileAggregate;
import com.darya.jobassistant.candidates.aggregate.CandidateProfilePreference;
import com.darya.jobassistant.candidates.aggregate.CandidateSkill;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A deterministic SHA-256 fingerprint of a {@link CandidateProfileAggregate}'s semantic content -
 * an audit/diagnostic aid for migration logs, not a replacement for {@link
 * CandidateProfileSemanticComparator}'s actual field-by-field comparison. {@code id}, {@code
 * version}, and any timestamp are never part of the input: two aggregates with identical business
 * content but different persistence identity/revision always fingerprint identically.
 *
 * <p>Uses an explicit, hand-built canonical string - never Java's default object serialization or
 * {@code toString()}, neither of which is a stable or even well-defined representation of record
 * content across JVM versions. Child collections are sorted before hashing (skills by name,
 * languages by code, preferences by type then value) so that a domain-collection's built-in
 * duplicate rejection guarantees stable ordering never causes two semantically identical profiles
 * to fingerprint differently just because their lists were built in a different order.
 */
public final class CandidateProfileFingerprint {

    /** A control character with no plausible legitimate appearance in a candidate profile field. */
    private static final String FIELD_SEPARATOR = "";
    private static final String RECORD_SEPARATOR = "";

    private CandidateProfileFingerprint() {
    }

    public static String sha256(CandidateProfileAggregate profile) {
        String canonical = canonicalRepresentation(profile);
        byte[] digest = digest(canonical);
        return HexFormat.of().formatHex(digest);
    }

    private static String canonicalRepresentation(CandidateProfileAggregate profile) {
        StringBuilder canonical = new StringBuilder();
        appendField(canonical, profile.profileKey());
        appendField(canonical, profile.targetRole());
        appendField(canonical, profile.seniority());
        appendField(canonical, String.valueOf(profile.experienceYears()));
        appendField(canonical, profile.preferredCompanyType());
        appendField(canonical, profile.preferredLocation());
        appendField(canonical, profile.employmentModel());
        appendField(canonical, profile.remotePolicy());
        appendField(canonical, profile.salaryCurrency());
        appendField(canonical, profile.minimumSalary() == null ? null : profile.minimumSalary().stripTrailingZeros().toPlainString());
        appendField(canonical, profile.currentCountry());
        appendField(canonical, String.valueOf(profile.relocationAllowed()));
        appendField(canonical, profile.salaryExpectationNote());
        appendField(canonical, profile.fullName());
        appendField(canonical, profile.email());
        appendField(canonical, profile.phone());
        appendField(canonical, profile.linkedinUrl());
        appendField(canonical, profile.cvLocation());
        appendField(canonical, profile.cvHeadline());
        appendField(canonical, canonicalSkills(profile.skills()));
        appendField(canonical, canonicalLanguages(profile.languages()));
        appendField(canonical, canonicalPreferences(profile.preferences()));
        appendField(canonical, canonicalEducation(profile.education()));
        return canonical.toString();
    }

    private static String canonicalSkills(List<CandidateSkill> skills) {
        return skills.stream()
                .sorted(Comparator.comparing(CandidateSkill::name))
                .map(skill -> join(skill.name(), skill.category(), skill.note(), skill.proficiency().name()))
                .reduce((a, b) -> a + RECORD_SEPARATOR + b)
                .orElse("");
    }

    /**
     * Sorted by {@link CandidateLanguage#displayOrder()} (Sprint 11 Step 5), not language code -
     * {@code displayOrder} is now the real, order-significant CV presentation fact, so two
     * profiles with the same languages in a different order are no longer semantically identical
     * (mirroring how an order-significant {@code CandidateProfilePreference} type is handled
     * below).
     */
    private static String canonicalLanguages(List<CandidateLanguage> languages) {
        return languages.stream()
                .sorted(Comparator.comparingInt(CandidateLanguage::displayOrder))
                .map(language -> join(language.languageCode(), language.proficiency(), String.valueOf(language.displayOrder())))
                .reduce((a, b) -> a + RECORD_SEPARATOR + b)
                .orElse("");
    }

    /** Sorted by {@link CandidateEducation#displayOrder()} - a candidate's declared education order is itself a real fact. */
    private static String canonicalEducation(List<CandidateEducation> education) {
        return education.stream()
                .sorted(Comparator.comparingInt(CandidateEducation::displayOrder))
                .map(entry -> join(
                        entry.institution(), entry.degree(), entry.fieldOfStudy(), entry.location(),
                        String.valueOf(entry.startDate()), String.valueOf(entry.endDate()), entry.description(),
                        String.valueOf(entry.displayOrder())))
                .reduce((a, b) -> a + RECORD_SEPARATOR + b)
                .orElse("");
    }

    /**
     * Grouped by {@code type} (in enum declaration order, via {@link EnumMap}, for a stable
     * iteration order across JVM runs) and, within each group, ordered per {@link
     * CandidatePreferenceType#isOrderSignificant()}: an order-significant type's rows are sorted
     * by {@link CandidateProfilePreference#priorityOrder()} (preserving - not erasing - a
     * meaningful reordering's effect on the fingerprint), everything else by {@code value} as
     * before this correction (collection order still carries no business meaning for those types).
     */
    private static String canonicalPreferences(List<CandidateProfilePreference> preferences) {
        Map<CandidatePreferenceType, List<CandidateProfilePreference>> byType =
                preferences.stream().collect(Collectors.groupingBy(
                        CandidateProfilePreference::type, () -> new EnumMap<>(CandidatePreferenceType.class), Collectors.toList()));
        return byType.entrySet().stream()
                .map(entry -> canonicalPreferenceGroup(entry.getKey(), entry.getValue()))
                .reduce((a, b) -> a + RECORD_SEPARATOR + b)
                .orElse("");
    }

    private static String canonicalPreferenceGroup(CandidatePreferenceType type, List<CandidateProfilePreference> group) {
        Comparator<CandidateProfilePreference> order = type.isOrderSignificant()
                ? Comparator.comparing(CandidateProfilePreference::priorityOrder)
                : Comparator.comparing(CandidateProfilePreference::value);
        return group.stream()
                .sorted(order)
                .map(preference -> join(
                        preference.type().name(), preference.value(), preference.importance() == null ? null : preference.importance().name()))
                .reduce((a, b) -> a + RECORD_SEPARATOR + b)
                .orElse("");
    }

    private static String join(String... parts) {
        StringBuilder joined = new StringBuilder();
        for (String part : parts) {
            appendField(joined, part);
        }
        return joined.toString();
    }

    private static void appendField(StringBuilder builder, String value) {
        builder.append(value == null ? "" : value).append(FIELD_SEPARATOR);
    }

    private static byte[] digest(String canonical) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return sha256.digest(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be available on every JVM", e);
        }
    }
}
