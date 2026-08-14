package com.darya.jobassistant.candidates.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.candidates.PreferenceImportance;
import com.darya.jobassistant.candidates.SkillProficiency;
import com.darya.jobassistant.candidates.aggregate.CandidateEducation;
import com.darya.jobassistant.candidates.aggregate.CandidateLanguage;
import com.darya.jobassistant.candidates.aggregate.CandidatePreferenceType;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileAggregate;
import com.darya.jobassistant.candidates.aggregate.CandidateProfilePreference;
import com.darya.jobassistant.candidates.aggregate.CandidateSkill;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CandidateProfileSemanticComparatorTest {

    @Test
    void diff_identicalContentDifferentIdentity_isEqualWithNoChangedFields() {
        CandidateProfileAggregate a = profile(UUID.randomUUID(), 0L);
        CandidateProfileAggregate b = profile(UUID.randomUUID(), 3L);

        CandidateProfileDiff diff = CandidateProfileSemanticComparator.diff(a, b);

        assertThat(diff.equal()).isTrue();
        assertThat(diff.changedFields()).isEmpty();
        assertThat(CandidateProfileSemanticComparator.areEqual(a, b)).isTrue();
    }

    @Test
    void diff_differentTargetRole_reportsTheFieldName_notItsValue() {
        CandidateProfileAggregate a = profile(UUID.randomUUID(), 0L);
        CandidateProfileAggregate b = withTargetRole(a, "Staff Java Backend Engineer");

        CandidateProfileDiff diff = CandidateProfileSemanticComparator.diff(a, b);

        assertThat(diff.equal()).isFalse();
        assertThat(diff.changedFields()).containsExactly("targetRole");
        for (String changedField : diff.changedFields()) {
            assertThat(changedField).doesNotContain("Staff Java Backend Engineer");
        }
    }

    @Test
    void diff_skillOrderingDifference_isNotReportedAsAChange() {
        CandidateProfileAggregate a = profileWithSkills(List.of(skill("Java"), skill("Kafka")));
        CandidateProfileAggregate b = profileWithSkills(List.of(skill("Kafka"), skill("Java")));

        assertThat(CandidateProfileSemanticComparator.areEqual(a, b)).isTrue();
    }

    @Test
    void diff_differentSkillSet_reportsSkillsChanged() {
        CandidateProfileAggregate a = profileWithSkills(List.of(skill("Java")));
        CandidateProfileAggregate b = profileWithSkills(List.of(skill("Kotlin")));

        CandidateProfileDiff diff = CandidateProfileSemanticComparator.diff(a, b);

        assertThat(diff.equal()).isFalse();
        assertThat(diff.changedFields()).contains("skills");
    }

    // ---- Preference ordering (Sprint 9 Step 3 correction) ----

    @Test
    void diff_reversedContractTypeOrder_reportsPreferencesChanged() {
        CandidateProfileAggregate a = profileWithContractTypes(List.of("B2B", "EOR"));
        CandidateProfileAggregate b = profileWithContractTypes(List.of("EOR", "B2B"));

        CandidateProfileDiff diff = CandidateProfileSemanticComparator.diff(a, b);

        assertThat(diff.equal()).isFalse();
        assertThat(diff.changedFields()).contains("preferences");
    }

    @Test
    void diff_reversedAllowedWorkCountryOrder_isNotReportedAsAChange() {
        CandidateProfileAggregate a = profileWithAllowedWorkCountries(List.of("Poland", "Germany"));
        CandidateProfileAggregate b = profileWithAllowedWorkCountries(List.of("Germany", "Poland"));

        assertThat(CandidateProfileSemanticComparator.areEqual(a, b)).isTrue();
    }

    private CandidateProfileAggregate profileWithContractTypes(List<String> contractTypesInOrder) {
        List<CandidateProfilePreference> preferences = new java.util.ArrayList<>();
        for (int i = 0; i < contractTypesInOrder.size(); i++) {
            preferences.add(new CandidateProfilePreference(
                    CandidatePreferenceType.CONTRACT_TYPE, contractTypesInOrder.get(i), PreferenceImportance.PREFERRED, i));
        }
        return new CandidateProfileAggregate(
                UUID.randomUUID(), "primary", "Backend Engineer", "Senior", 6,
                null, null, null, null, null, null,
                null, false, null, List.of(), List.of(), preferences, 0L);
    }

    private CandidateProfileAggregate profileWithAllowedWorkCountries(List<String> countries) {
        List<CandidateProfilePreference> preferences = countries.stream()
                .map(country -> new CandidateProfilePreference(CandidatePreferenceType.ALLOWED_WORK_COUNTRY, country, null))
                .toList();
        return new CandidateProfileAggregate(
                UUID.randomUUID(), "primary", "Backend Engineer", "Senior", 6,
                null, null, null, null, null, null,
                null, false, null, List.of(), List.of(), preferences, 0L);
    }

    private CandidateProfileAggregate profile(UUID id, long version) {
        return new CandidateProfileAggregate(
                id, "primary", "Senior Java Backend Engineer", "Senior", 6,
                null, null, null, null, null, null,
                null, false, null, List.of(), List.of(), List.of(), version);
    }

    // ---- Sprint 11 Step 5: header facts, education, and language ordering ----

    @Test
    void diff_differentCvHeadline_isReportedAsAChange() {
        CandidateProfileAggregate a = withCvHeadline(profile(UUID.randomUUID(), 0L), "Senior Java Backend Engineer");
        CandidateProfileAggregate b = withCvHeadline(profile(UUID.randomUUID(), 0L), "Staff Java Backend Engineer");

        CandidateProfileDiff diff = CandidateProfileSemanticComparator.diff(a, b);

        assertThat(diff.equal()).isFalse();
        assertThat(diff.changedFields()).contains("cvHeadline");
    }

    @Test
    void diff_differentEducation_isReportedAsAChange() {
        CandidateEducation educationA = new CandidateEducation("University A", null, null, null, null, null, null, 0);
        CandidateEducation educationB = new CandidateEducation("University B", null, null, null, null, null, null, 0);
        CandidateProfileAggregate a = withEducation(profile(UUID.randomUUID(), 0L), List.of(educationA));
        CandidateProfileAggregate b = withEducation(profile(UUID.randomUUID(), 0L), List.of(educationB));

        assertThat(CandidateProfileSemanticComparator.areEqual(a, b)).isFalse();
        assertThat(CandidateProfileSemanticComparator.diff(a, b).changedFields()).contains("education");
    }

    @Test
    void diff_reversedLanguageDisplayOrder_isReportedAsAChange() {
        CandidateProfileAggregate a = withLanguages(profile(UUID.randomUUID(), 0L),
                List.of(new CandidateLanguage("en", null, 0), new CandidateLanguage("pl", null, 1)));
        CandidateProfileAggregate b = withLanguages(profile(UUID.randomUUID(), 0L),
                List.of(new CandidateLanguage("en", null, 1), new CandidateLanguage("pl", null, 0)));

        assertThat(CandidateProfileSemanticComparator.areEqual(a, b)).isFalse();
        assertThat(CandidateProfileSemanticComparator.diff(a, b).changedFields()).contains("languages");
    }

    private CandidateProfileAggregate withCvHeadline(CandidateProfileAggregate profile, String cvHeadline) {
        return new CandidateProfileAggregate(
                profile.id(), profile.profileKey(), profile.targetRole(), profile.seniority(), profile.experienceYears(),
                profile.preferredCompanyType(), profile.preferredLocation(), profile.employmentModel(), profile.remotePolicy(),
                profile.salaryCurrency(), profile.minimumSalary(), profile.currentCountry(), profile.relocationAllowed(),
                profile.salaryExpectationNote(), profile.email(), profile.phone(), profile.linkedinUrl(), profile.cvLocation(),
                cvHeadline, profile.skills(), profile.languages(), profile.preferences(), profile.education(), profile.version());
    }

    private CandidateProfileAggregate withEducation(CandidateProfileAggregate profile, List<CandidateEducation> education) {
        return new CandidateProfileAggregate(
                profile.id(), profile.profileKey(), profile.targetRole(), profile.seniority(), profile.experienceYears(),
                profile.preferredCompanyType(), profile.preferredLocation(), profile.employmentModel(), profile.remotePolicy(),
                profile.salaryCurrency(), profile.minimumSalary(), profile.currentCountry(), profile.relocationAllowed(),
                profile.salaryExpectationNote(), profile.email(), profile.phone(), profile.linkedinUrl(), profile.cvLocation(),
                profile.cvHeadline(), profile.skills(), profile.languages(), profile.preferences(), education, profile.version());
    }

    private CandidateProfileAggregate withLanguages(CandidateProfileAggregate profile, List<CandidateLanguage> languages) {
        return new CandidateProfileAggregate(
                profile.id(), profile.profileKey(), profile.targetRole(), profile.seniority(), profile.experienceYears(),
                profile.preferredCompanyType(), profile.preferredLocation(), profile.employmentModel(), profile.remotePolicy(),
                profile.salaryCurrency(), profile.minimumSalary(), profile.currentCountry(), profile.relocationAllowed(),
                profile.salaryExpectationNote(), profile.email(), profile.phone(), profile.linkedinUrl(), profile.cvLocation(),
                profile.cvHeadline(), profile.skills(), languages, profile.preferences(), profile.education(), profile.version());
    }

    private CandidateProfileAggregate withTargetRole(CandidateProfileAggregate profile, String targetRole) {
        return new CandidateProfileAggregate(
                profile.id(), profile.profileKey(), targetRole, profile.seniority(), profile.experienceYears(),
                profile.preferredCompanyType(), profile.preferredLocation(), profile.employmentModel(), profile.remotePolicy(),
                profile.salaryCurrency(), profile.minimumSalary(), profile.currentCountry(), profile.relocationAllowed(),
                profile.salaryExpectationNote(), profile.skills(), profile.languages(), profile.preferences(), profile.version());
    }

    private CandidateProfileAggregate profileWithSkills(List<CandidateSkill> skills) {
        return new CandidateProfileAggregate(
                UUID.randomUUID(), "primary", "Backend Engineer", "Senior", 6,
                null, null, null, null, null, null,
                null, false, null, skills, List.of(new CandidateLanguage("en", null, 0)), List.of(), 0L);
    }

    private CandidateSkill skill(String name) {
        return new CandidateSkill(name, null, null, SkillProficiency.STRONG);
    }
}
