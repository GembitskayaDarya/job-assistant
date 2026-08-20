package com.darya.jobassistant.candidates.migration;

import com.darya.jobassistant.candidates.aggregate.CandidateEducation;
import com.darya.jobassistant.candidates.aggregate.CandidateLanguage;
import com.darya.jobassistant.candidates.aggregate.CandidatePreferenceType;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileAggregate;
import com.darya.jobassistant.candidates.aggregate.CandidateProfilePreference;
import com.darya.jobassistant.candidates.aggregate.CandidateSkill;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Explicit field-by-field semantic comparison of two {@link CandidateProfileAggregate} instances -
 * ignores {@code id}, {@code version}, and any timestamp (none of which are even part of this
 * type), and treats {@link CandidateProfileAggregate#skills()}/{@link
 * CandidateProfileAggregate#languages()} as sets (collection order carries no business meaning -
 * the domain itself already forbids duplicates within one profile). {@link
 * CandidateProfileAggregate#preferences()} is mixed: preference types where {@link
 * CandidatePreferenceType#isOrderSignificant()} is {@code true} (currently only {@code
 * CONTRACT_TYPE} - see its javadoc for the concrete evidence) are compared positionally, since a
 * reordering of such a list is itself a real semantic change; every other type is still compared
 * as a set. Not implemented via record {@code equals()}: that would compare {@code id} and {@code
 * version} too, making a freshly-mapped source (always {@code id=null}, {@code version=0}) and a
 * loaded destination (a real id/version) look different even when every business field genuinely
 * matches.
 */
public final class CandidateProfileSemanticComparator {

    private CandidateProfileSemanticComparator() {
    }

    public static boolean areEqual(CandidateProfileAggregate a, CandidateProfileAggregate b) {
        return diff(a, b).equal();
    }

    public static CandidateProfileDiff diff(CandidateProfileAggregate a, CandidateProfileAggregate b) {
        List<String> changed = new ArrayList<>();
        addAnalysisScopedDifferences(changed, a, b);
        addHeaderAndEducationDifferences(changed, a, b);
        return new CandidateProfileDiff(changed.isEmpty(), changed);
    }

    /**
     * Acceptance correction: {@link CandidateProfileParityVerifier} round-trips {@code reloaded}
     * through {@link CandidateProfileAnalysisAssembler#toAnalysisProfile} - which intentionally
     * drops {@link CandidateProfileAggregate#fullName()}/email/phone/linkedinUrl/cvLocation/
     * cvHeadline/education, since none of those are part of the analysis-profile shape {@code
     * JobAnalysisService} actually consumes (see that assembler's javadoc). Comparing the full
     * {@link #diff} (which does check those fields) against that deliberately-narrowed round trip
     * would report a false parity failure for every profile with real header-fact data - this
     * method exists so the parity verifier can check only the fields the analysis round trip is
     * actually meant to preserve; {@link #headerAndEducationFieldsEqual} covers the rest directly
     * against the freshly-persisted aggregate instead.
     */
    static boolean analysisScopedFieldsEqual(CandidateProfileAggregate a, CandidateProfileAggregate b) {
        List<String> changed = new ArrayList<>();
        addAnalysisScopedDifferences(changed, a, b);
        return changed.isEmpty();
    }

    /** See {@link #analysisScopedFieldsEqual} - the complementary half of the same split. */
    static boolean headerAndEducationFieldsEqual(CandidateProfileAggregate a, CandidateProfileAggregate b) {
        List<String> changed = new ArrayList<>();
        addHeaderAndEducationDifferences(changed, a, b);
        return changed.isEmpty();
    }

    private static void addAnalysisScopedDifferences(List<String> changed, CandidateProfileAggregate a, CandidateProfileAggregate b) {
        addIfDifferent(changed, "profileKey", a.profileKey(), b.profileKey());
        addIfDifferent(changed, "targetRole", a.targetRole(), b.targetRole());
        addIfDifferent(changed, "seniority", a.seniority(), b.seniority());
        addIfDifferent(changed, "experienceYears", a.experienceYears(), b.experienceYears());
        addIfDifferent(changed, "preferredCompanyType", a.preferredCompanyType(), b.preferredCompanyType());
        addIfDifferent(changed, "preferredLocation", a.preferredLocation(), b.preferredLocation());
        addIfDifferent(changed, "employmentModel", a.employmentModel(), b.employmentModel());
        addIfDifferent(changed, "remotePolicy", a.remotePolicy(), b.remotePolicy());
        addIfDifferent(changed, "salaryCurrency", a.salaryCurrency(), b.salaryCurrency());
        addIfDifferent(changed, "minimumSalary", normalizedSalary(a), normalizedSalary(b));
        addIfDifferent(changed, "currentCountry", a.currentCountry(), b.currentCountry());
        addIfDifferent(changed, "relocationAllowed", a.relocationAllowed(), b.relocationAllowed());
        addIfDifferent(changed, "salaryExpectationNote", a.salaryExpectationNote(), b.salaryExpectationNote());
        if (!skillSet(a.skills()).equals(skillSet(b.skills()))) {
            changed.add("skills");
        }
        if (!orderedLanguages(a.languages()).equals(orderedLanguages(b.languages()))) {
            changed.add("languages");
        }
        if (!preferencesEqual(a.preferences(), b.preferences())) {
            changed.add("preferences");
        }
    }

    private static void addHeaderAndEducationDifferences(List<String> changed, CandidateProfileAggregate a, CandidateProfileAggregate b) {
        addIfDifferent(changed, "fullName", a.fullName(), b.fullName());
        addIfDifferent(changed, "email", a.email(), b.email());
        addIfDifferent(changed, "phone", a.phone(), b.phone());
        addIfDifferent(changed, "linkedinUrl", a.linkedinUrl(), b.linkedinUrl());
        addIfDifferent(changed, "cvLocation", a.cvLocation(), b.cvLocation());
        addIfDifferent(changed, "cvHeadline", a.cvHeadline(), b.cvHeadline());
        if (!orderedEducation(a.education()).equals(orderedEducation(b.education()))) {
            changed.add("education");
        }
    }

    private static String normalizedSalary(CandidateProfileAggregate profile) {
        return profile.minimumSalary() == null ? null : profile.minimumSalary().stripTrailingZeros().toPlainString();
    }

    private static Set<String> skillSet(List<CandidateSkill> skills) {
        return skills.stream()
                .map(skill -> skill.name() + '' + skill.category() + '' + skill.note() + '' + skill.proficiency())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * Ordered by {@link CandidateLanguage#displayOrder()} (Sprint 11 Step 5) - unlike {@link
     * #skillSet}, language order is now a real, order-significant CV presentation fact (see
     * {@code CandidateProfileFingerprint#canonicalLanguages}'s javadoc for the same reasoning), so
     * this is compared positionally, not as a set.
     */
    private static List<String> orderedLanguages(List<CandidateLanguage> languages) {
        return languages.stream()
                .sorted(Comparator.comparingInt(CandidateLanguage::displayOrder))
                .map(language -> language.languageCode() + '' + language.proficiency() + '' + language.displayOrder())
                .toList();
    }

    /** Ordered by {@link CandidateEducation#displayOrder()} - a candidate's declared education order is itself a real fact. */
    private static List<String> orderedEducation(List<CandidateEducation> education) {
        return education.stream()
                .sorted(Comparator.comparingInt(CandidateEducation::displayOrder))
                .map(entry -> entry.institution() + '' + entry.degree() + '' + entry.fieldOfStudy() + ''
                        + entry.location() + '' + entry.startDate() + '' + entry.endDate() + ''
                        + entry.description() + '' + entry.displayOrder())
                .toList();
    }

    /**
     * Order-significant types (see class javadoc) are compared as a positional list per type;
     * every other type is compared as a set, exactly as before this correction.
     */
    private static boolean preferencesEqual(List<CandidateProfilePreference> a, List<CandidateProfilePreference> b) {
        if (!unorderedPreferenceSet(a).equals(unorderedPreferenceSet(b))) {
            return false;
        }
        for (CandidatePreferenceType type : CandidatePreferenceType.values()) {
            if (type.isOrderSignificant() && !orderedPreferenceValues(a, type).equals(orderedPreferenceValues(b, type))) {
                return false;
            }
        }
        return true;
    }

    private static Set<String> unorderedPreferenceSet(List<CandidateProfilePreference> preferences) {
        return preferences.stream()
                .filter(preference -> !preference.type().isOrderSignificant())
                .map(preference -> preference.type() + "" + preference.value() + '' + preference.importance())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static List<String> orderedPreferenceValues(List<CandidateProfilePreference> preferences, CandidatePreferenceType type) {
        return preferences.stream()
                .filter(preference -> preference.type() == type)
                .sorted(Comparator.comparing(CandidateProfilePreference::priorityOrder))
                .map(preference -> preference.value() + '' + preference.importance())
                .toList();
    }

    private static void addIfDifferent(List<String> changed, String fieldName, Object a, Object b) {
        if (!Objects.equals(a, b)) {
            changed.add(fieldName);
        }
    }
}
