package com.darya.jobassistant.candidates.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateSkill;
import com.darya.jobassistant.candidates.PreferenceImportance;
import com.darya.jobassistant.candidates.SkillProficiency;
import com.darya.jobassistant.candidates.aggregate.CandidatePreferenceType;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileAggregate;
import com.darya.jobassistant.candidates.aggregate.CandidateProfilePreference;
import java.util.List;
import org.junit.jupiter.api.Test;

class CandidateProfileYamlImportMapperTest {

    private final CandidatePreferences preferences = new CandidatePreferences(
            "Poland", "Remote", PreferenceImportance.STRONG, List.of("Poland", "Germany"), true,
            List.of("B2B", "Full-time"), PreferenceImportance.PREFERRED, "Product", PreferenceImportance.PREFERRED,
            "8000 EUR/month");

    private final CandidateProfile source = new CandidateProfile(
            "Senior Java Backend Engineer", "Senior",
            List.of(new CandidateSkill("Java", SkillProficiency.EXPERT, "10+ years commercial use"),
                    new CandidateSkill("Kafka", SkillProficiency.WORKING, null)),
            List.of("English", "Polish"),
            6, preferences);

    @Test
    void toAggregate_mapsEveryScalarFieldUsedByAnalysis() {
        CandidateProfileAggregate aggregate = CandidateProfileYamlImportMapper.toAggregate(source, "primary");

        assertThat(aggregate.id()).isNull();
        assertThat(aggregate.profileKey()).isEqualTo("primary");
        assertThat(aggregate.targetRole()).isEqualTo("Senior Java Backend Engineer");
        assertThat(aggregate.seniority()).isEqualTo("Senior");
        assertThat(aggregate.experienceYears()).isEqualTo(6);
        assertThat(aggregate.version()).isZero();
    }

    @Test
    void toAggregate_skillsPreserveNameCategoryNoteAndProficiency() {
        CandidateProfileAggregate aggregate = CandidateProfileYamlImportMapper.toAggregate(source, "primary");

        assertThat(aggregate.skills()).hasSize(2);
        var java = aggregate.skills().stream().filter(s -> s.name().equals("Java")).findFirst().orElseThrow();
        assertThat(java.category()).isNull();
        assertThat(java.note()).isEqualTo("10+ years commercial use");
        assertThat(java.proficiency()).isEqualTo(SkillProficiency.EXPERT);
    }

    @Test
    void toAggregate_neverIntroducesNoneProficiency() {
        CandidateProfileAggregate aggregate = CandidateProfileYamlImportMapper.toAggregate(source, "primary");

        assertThat(aggregate.skills()).extracting(s -> s.proficiency().name())
                .allMatch(name -> List.of("BASIC", "WORKING", "STRONG", "EXPERT").contains(name));
    }

    @Test
    void toAggregate_languagesAreNormalizedToLowercaseIsoCodes() {
        CandidateProfileAggregate aggregate = CandidateProfileYamlImportMapper.toAggregate(source, "primary");

        assertThat(aggregate.languages()).extracting(l -> l.languageCode()).containsExactlyInAnyOrder("en", "pl");
    }

    @Test
    void toAggregate_unrecognizedLanguageName_failsValidation_ratherThanBeingDiscarded() {
        CandidateProfile withUnknownLanguage = new CandidateProfile(
                "Backend Engineer", "Senior", List.of(), List.of("Klingon"), 6, preferences);

        assertThatThrownBy(() -> CandidateProfileYamlImportMapper.toAggregate(withUnknownLanguage, "primary"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Klingon");
    }

    @Test
    void toAggregate_weightedPreferencesPreserveValuesAndImportance() {
        CandidateProfileAggregate aggregate = CandidateProfileYamlImportMapper.toAggregate(source, "primary");

        assertThat(aggregate.preferences()).contains(
                new CandidateProfilePreference(CandidatePreferenceType.WORK_ARRANGEMENT, "Remote", PreferenceImportance.STRONG),
                new CandidateProfilePreference(CandidatePreferenceType.ALLOWED_WORK_COUNTRY, "Poland", null),
                new CandidateProfilePreference(CandidatePreferenceType.ALLOWED_WORK_COUNTRY, "Germany", null),
                new CandidateProfilePreference(CandidatePreferenceType.CONTRACT_TYPE, "B2B", PreferenceImportance.PREFERRED, 0),
                new CandidateProfilePreference(CandidatePreferenceType.CONTRACT_TYPE, "Full-time", PreferenceImportance.PREFERRED, 1),
                new CandidateProfilePreference(CandidatePreferenceType.COMPANY_TYPE, "Product", PreferenceImportance.PREFERRED));
    }

    @Test
    void toAggregate_contractTypes_priorityOrderMatchesSourceListPosition() {
        CandidateProfileAggregate aggregate = CandidateProfileYamlImportMapper.toAggregate(source, "primary");

        var b2b = aggregate.preferences().stream()
                .filter(p -> p.type() == CandidatePreferenceType.CONTRACT_TYPE && p.value().equals("B2B")).findFirst().orElseThrow();
        var fullTime = aggregate.preferences().stream()
                .filter(p -> p.type() == CandidatePreferenceType.CONTRACT_TYPE && p.value().equals("Full-time")).findFirst().orElseThrow();
        assertThat(b2b.priorityOrder()).isZero();
        assertThat(fullTime.priorityOrder()).isEqualTo(1);
    }

    @Test
    void toAggregate_allowedWorkCountries_neverCarryAPriorityOrder() {
        CandidateProfileAggregate aggregate = CandidateProfileYamlImportMapper.toAggregate(source, "primary");

        assertThat(aggregate.preferences().stream()
                .filter(p -> p.type() == CandidatePreferenceType.ALLOWED_WORK_COUNTRY))
                .allMatch(p -> p.priorityOrder() == null);
    }

    @Test
    void toAggregate_scalarPreferencesMappedToFlatColumns() {
        CandidateProfileAggregate aggregate = CandidateProfileYamlImportMapper.toAggregate(source, "primary");

        assertThat(aggregate.currentCountry()).isEqualTo("Poland");
        assertThat(aggregate.relocationAllowed()).isTrue();
        assertThat(aggregate.salaryExpectationNote()).isEqualTo("8000 EUR/month");
    }

    @Test
    void toAggregate_legacyFlatCompanyTypeAndRemotePolicy_areNeverPopulated() {
        CandidateProfileAggregate aggregate = CandidateProfileYamlImportMapper.toAggregate(source, "primary");

        assertThat(aggregate.preferredCompanyType()).isNull();
        assertThat(aggregate.remotePolicy()).isNull();
        assertThat(aggregate.preferredLocation()).isNull();
        assertThat(aggregate.employmentModel()).isNull();
        assertThat(aggregate.salaryCurrency()).isNull();
        assertThat(aggregate.minimumSalary()).isNull();
    }

    @Test
    void toAggregate_emptyOptionalPreferences_areHandledDeliberately_notLeftAsPreferenceRows() {
        CandidatePreferences minimal = new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null);
        CandidateProfile minimalSource = new CandidateProfile("Backend Engineer", "Senior", List.of(), List.of(), 6, minimal);

        CandidateProfileAggregate aggregate = CandidateProfileYamlImportMapper.toAggregate(minimalSource, "primary");

        assertThat(aggregate.preferences()).isEmpty();
        assertThat(aggregate.currentCountry()).isNull();
        assertThat(aggregate.relocationAllowed()).isFalse();
        assertThat(aggregate.salaryExpectationNote()).isNull();
    }

    @Test
    void toAggregate_nullSource_isRejected() {
        assertThatThrownBy(() -> CandidateProfileYamlImportMapper.toAggregate(null, "primary"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
