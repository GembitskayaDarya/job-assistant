package com.darya.jobassistant.candidates.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.candidates.CandidateEducationEntry;
import com.darya.jobassistant.candidates.CandidateLanguageEntry;
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
            List.of(new CandidateLanguageEntry("English", "Fluent"), new CandidateLanguageEntry("Polish", "Conversational")),
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

    /** Acceptance correction: the previous plain {@code List<String>} shape had no field for this, so proficiency was always imported as null. */
    @Test
    void toAggregate_languageProficiencyIsCarriedThroughUnchanged() {
        CandidateProfileAggregate aggregate = CandidateProfileYamlImportMapper.toAggregate(source, "primary");

        var english = aggregate.languages().stream().filter(l -> l.languageCode().equals("en")).findFirst().orElseThrow();
        var polish = aggregate.languages().stream().filter(l -> l.languageCode().equals("pl")).findFirst().orElseThrow();
        assertThat(english.proficiency()).isEqualTo("Fluent");
        assertThat(polish.proficiency()).isEqualTo("Conversational");
    }

    @Test
    void toAggregate_unrecognizedLanguageName_failsValidation_ratherThanBeingDiscarded() {
        CandidateProfile withUnknownLanguage = new CandidateProfile(
                "Backend Engineer", "Senior", List.of(), List.of(new CandidateLanguageEntry("Klingon", "Fluent")), 6, preferences);

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

    // ---- Sprint 11 Step 5: CV header/contact facts and education ----

    @Test
    void toAggregate_headerFields_areMappedUnchanged() {
        CandidateProfile withHeader = new CandidateProfile(
                "Senior Java Backend Engineer", "Senior", List.of(), List.of(), 6, preferences,
                "Jane Doe", "person@example.com", "+48123456789", "https://www.linkedin.com/in/example", "Warsaw, Poland",
                "Senior Java Backend Engineer", List.of());

        CandidateProfileAggregate aggregate = CandidateProfileYamlImportMapper.toAggregate(withHeader, "primary");

        assertThat(aggregate.fullName()).isEqualTo("Jane Doe");
        assertThat(aggregate.email()).isEqualTo("person@example.com");
        assertThat(aggregate.phone()).isEqualTo("+48123456789");
        assertThat(aggregate.linkedinUrl()).isEqualTo("https://www.linkedin.com/in/example");
        assertThat(aggregate.cvLocation()).isEqualTo("Warsaw, Poland");
        assertThat(aggregate.cvHeadline()).isEqualTo("Senior Java Backend Engineer");
    }

    @Test
    void toAggregate_education_displayOrderMatchesSourceListPosition() {
        CandidateProfile withEducation = new CandidateProfile(
                "Backend Engineer", "Senior", List.of(), List.of(), 6, preferences,
                null, null, null, null, null, null,
                List.of(
                        new CandidateEducationEntry("University B", null, null, null, null, null, null),
                        new CandidateEducationEntry("University A", null, null, null, null, null, null)));

        CandidateProfileAggregate aggregate = CandidateProfileYamlImportMapper.toAggregate(withEducation, "primary");

        assertThat(aggregate.education()).extracting(e -> e.institution()).containsExactly("University B", "University A");
        assertThat(aggregate.education().get(0).displayOrder()).isZero();
        assertThat(aggregate.education().get(1).displayOrder()).isEqualTo(1);
    }

    @Test
    void toAggregate_education_onlyInstitutionIsRequired() {
        CandidateProfile withMinimalEducation = new CandidateProfile(
                "Backend Engineer", "Senior", List.of(), List.of(), 6, preferences,
                null, null, null, null, null, null,
                List.of(new CandidateEducationEntry("Example University", null, null, null, null, null, null)));

        CandidateProfileAggregate aggregate = CandidateProfileYamlImportMapper.toAggregate(withMinimalEducation, "primary");

        assertThat(aggregate.education()).hasSize(1);
        assertThat(aggregate.education().get(0).degree()).isNull();
    }

    @Test
    void toAggregate_languages_displayOrderMatchesSourceListPosition() {
        CandidateProfileAggregate aggregate = CandidateProfileYamlImportMapper.toAggregate(source, "primary");

        var english = aggregate.languages().stream().filter(l -> l.languageCode().equals("en")).findFirst().orElseThrow();
        var polish = aggregate.languages().stream().filter(l -> l.languageCode().equals("pl")).findFirst().orElseThrow();
        assertThat(english.displayOrder()).isZero();
        assertThat(polish.displayOrder()).isEqualTo(1);
    }
}
