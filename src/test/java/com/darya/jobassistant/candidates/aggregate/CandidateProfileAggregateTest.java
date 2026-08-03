package com.darya.jobassistant.candidates.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.candidates.PreferenceImportance;
import com.darya.jobassistant.candidates.SkillProficiency;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CandidateProfileAggregateTest {

    private final List<CandidateSkill> skills = List.of(
            new CandidateSkill("Java", "Language", null, SkillProficiency.EXPERT),
            new CandidateSkill("Spring Boot", "Framework", null, SkillProficiency.STRONG));

    private final List<CandidateLanguage> languages = List.of(
            new CandidateLanguage("en", "FLUENT"));

    private final List<CandidateProfilePreference> preferences = List.of(
            new CandidateProfilePreference(CandidatePreferenceType.WORK_ARRANGEMENT, "Remote", PreferenceImportance.STRONG),
            new CandidateProfilePreference(CandidatePreferenceType.ALLOWED_WORK_COUNTRY, "Poland", null),
            new CandidateProfilePreference(CandidatePreferenceType.CONTRACT_TYPE, "B2B", PreferenceImportance.PREFERRED, 0));

    @Test
    void constructor_validProfile_isCreated() {
        UUID id = UUID.randomUUID();

        CandidateProfileAggregate profile = new CandidateProfileAggregate(
                id, "primary", "Senior Java Backend Engineer", "Senior", 6,
                null, "Europe", null, null, "EUR", new BigDecimal("8000.00"),
                "Poland", false, "120000 PLN/year",
                skills, languages, preferences, 0L);

        assertThat(profile.id()).isEqualTo(id);
        assertThat(profile.profileKey()).isEqualTo("primary");
        assertThat(profile.targetRole()).isEqualTo("Senior Java Backend Engineer");
        assertThat(profile.seniority()).isEqualTo("Senior");
        assertThat(profile.experienceYears()).isEqualTo(6);
        assertThat(profile.preferredLocation()).isEqualTo("Europe");
        assertThat(profile.salaryCurrency()).isEqualTo("EUR");
        assertThat(profile.minimumSalary()).isEqualByComparingTo("8000.00");
        assertThat(profile.currentCountry()).isEqualTo("Poland");
        assertThat(profile.relocationAllowed()).isFalse();
        assertThat(profile.salaryExpectationNote()).isEqualTo("120000 PLN/year");
        assertThat(profile.skills()).containsExactlyElementsOf(skills);
        assertThat(profile.languages()).containsExactlyElementsOf(languages);
        assertThat(profile.preferences()).containsExactlyElementsOf(preferences);
        assertThat(profile.version()).isZero();
    }

    @Test
    void constructor_nullIdAndOptionalFields_areAllowed_representingANotYetPersistedProfile() {
        CandidateProfileAggregate profile = new CandidateProfileAggregate(
                null, "primary", "Backend Engineer", null, 0,
                null, null, null, null, null, null,
                null, false, null,
                null, null, null, 0L);

        assertThat(profile.id()).isNull();
        assertThat(profile.skills()).isEmpty();
        assertThat(profile.languages()).isEmpty();
        assertThat(profile.preferences()).isEmpty();
    }

    @Test
    void constructor_mutatingSourceLists_doesNotAffectStoredState() {
        List<CandidateSkill> mutableSkills = new ArrayList<>(skills);
        List<CandidateLanguage> mutableLanguages = new ArrayList<>(languages);
        List<CandidateProfilePreference> mutablePreferences = new ArrayList<>(preferences);

        CandidateProfileAggregate profile = profileWith(mutableSkills, mutableLanguages, mutablePreferences);

        mutableSkills.add(new CandidateSkill("Kafka", null, null, SkillProficiency.BASIC));
        mutableLanguages.add(new CandidateLanguage("pl", null));
        mutablePreferences.add(new CandidateProfilePreference(CandidatePreferenceType.COMPANY_TYPE, "Product", PreferenceImportance.PREFERRED));

        assertThat(profile.skills()).hasSize(2);
        assertThat(profile.languages()).hasSize(1);
        assertThat(profile.preferences()).hasSize(3);
    }

    @Test
    void accessors_skillsLanguagesAndPreferences_areUnmodifiable() {
        CandidateProfileAggregate profile = profileWith(skills, languages, preferences);

        assertThatThrownBy(() -> profile.skills().add(new CandidateSkill("Kotlin", null, null, SkillProficiency.BASIC)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> profile.languages().add(new CandidateLanguage("ru", null)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> profile.preferences().add(
                new CandidateProfilePreference(CandidatePreferenceType.COMPANY_TYPE, "Product", PreferenceImportance.STRONG)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void constructor_blankProfileKey_isRejected() {
        assertThatThrownBy(() -> profileWithKey("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullProfileKey_isRejected() {
        assertThatThrownBy(() -> profileWithKey(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_blankTargetRole_isRejected() {
        assertThatThrownBy(() -> new CandidateProfileAggregate(
                null, "primary", "   ", null, 0, null, null, null, null, null, null,
                null, false, null, skills, languages, preferences, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_negativeExperienceYears_isRejected() {
        assertThatThrownBy(() -> new CandidateProfileAggregate(
                null, "primary", "Backend Engineer", null, -1, null, null, null, null, null, null,
                null, false, null, skills, languages, preferences, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_negativeMinimumSalary_isRejected() {
        assertThatThrownBy(() -> new CandidateProfileAggregate(
                null, "primary", "Backend Engineer", null, 0, null, null, null, null, null,
                new BigDecimal("-1.00"), null, false, null, skills, languages, preferences, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_negativeVersion_isRejected() {
        assertThatThrownBy(() -> new CandidateProfileAggregate(
                null, "primary", "Backend Engineer", null, 0, null, null, null, null, null, null,
                null, false, null, skills, languages, preferences, -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_duplicateSkillNames_areRejected() {
        List<CandidateSkill> duplicateSkills = List.of(
                new CandidateSkill("Java", null, null, SkillProficiency.EXPERT),
                new CandidateSkill("Java", null, null, SkillProficiency.BASIC));

        assertThatThrownBy(() -> profileWith(duplicateSkills, languages, List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_duplicateLanguageCodes_areRejected() {
        List<CandidateLanguage> duplicateLanguages = List.of(
                new CandidateLanguage("en", "FLUENT"),
                new CandidateLanguage("en", "NATIVE"));

        assertThatThrownBy(() -> profileWith(skills, duplicateLanguages, List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_duplicatePreferenceTypeAndValue_isRejected() {
        List<CandidateProfilePreference> duplicatePreferences = List.of(
                new CandidateProfilePreference(CandidatePreferenceType.ALLOWED_WORK_COUNTRY, "Poland", null),
                new CandidateProfilePreference(CandidatePreferenceType.ALLOWED_WORK_COUNTRY, "Poland", null));

        assertThatThrownBy(() -> profileWith(skills, languages, duplicatePreferences)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_multipleWorkArrangementPreferences_isRejected() {
        List<CandidateProfilePreference> twoWorkArrangements = List.of(
                new CandidateProfilePreference(CandidatePreferenceType.WORK_ARRANGEMENT, "Remote", PreferenceImportance.STRONG),
                new CandidateProfilePreference(CandidatePreferenceType.WORK_ARRANGEMENT, "Hybrid", PreferenceImportance.NEUTRAL));

        assertThatThrownBy(() -> profileWith(skills, languages, twoWorkArrangements)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_multipleCompanyTypePreferences_isRejected() {
        List<CandidateProfilePreference> twoCompanyTypes = List.of(
                new CandidateProfilePreference(CandidatePreferenceType.COMPANY_TYPE, "Product", PreferenceImportance.PREFERRED),
                new CandidateProfilePreference(CandidatePreferenceType.COMPANY_TYPE, "Startup", PreferenceImportance.NEUTRAL));

        assertThatThrownBy(() -> profileWith(skills, languages, twoCompanyTypes)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_multipleAllowedWorkCountriesAndContractTypes_areAccepted() {
        List<CandidateProfilePreference> multiValued = List.of(
                new CandidateProfilePreference(CandidatePreferenceType.ALLOWED_WORK_COUNTRY, "Poland", null),
                new CandidateProfilePreference(CandidatePreferenceType.ALLOWED_WORK_COUNTRY, "Germany", null),
                new CandidateProfilePreference(CandidatePreferenceType.CONTRACT_TYPE, "B2B", PreferenceImportance.PREFERRED, 0),
                new CandidateProfilePreference(CandidatePreferenceType.CONTRACT_TYPE, "Full-time", PreferenceImportance.PREFERRED, 1));

        CandidateProfileAggregate profile = profileWith(skills, languages, multiValued);

        assertThat(profile.preferences()).hasSize(4);
    }

    @Test
    void constructor_duplicatePriorityOrderWithinSameOrderSignificantType_isRejected() {
        List<CandidateProfilePreference> duplicatePriorityOrder = List.of(
                new CandidateProfilePreference(CandidatePreferenceType.CONTRACT_TYPE, "B2B", PreferenceImportance.PREFERRED, 0),
                new CandidateProfilePreference(CandidatePreferenceType.CONTRACT_TYPE, "Full-time", PreferenceImportance.PREFERRED, 0));

        assertThatThrownBy(() -> profileWith(skills, languages, duplicatePriorityOrder)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_companyTypePreferenceAndFlatPreferredCompanyType_isRejected() {
        List<CandidateProfilePreference> companyTypePreference = List.of(
                new CandidateProfilePreference(CandidatePreferenceType.COMPANY_TYPE, "Product", PreferenceImportance.PREFERRED));

        assertThatThrownBy(() -> new CandidateProfileAggregate(
                null, "primary", "Backend Engineer", null, 0, "Product", null, null, null, null, null,
                null, false, null, skills, languages, companyTypePreference, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_workArrangementPreferenceAndFlatRemotePolicy_isRejected() {
        List<CandidateProfilePreference> workArrangementPreference = List.of(
                new CandidateProfilePreference(CandidatePreferenceType.WORK_ARRANGEMENT, "Remote", PreferenceImportance.STRONG));

        assertThatThrownBy(() -> new CandidateProfileAggregate(
                null, "primary", "Backend Engineer", null, 0, null, null, null, "REMOTE", null, null,
                null, false, null, skills, languages, workArrangementPreference, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private CandidateProfileAggregate profileWith(
            List<CandidateSkill> skills, List<CandidateLanguage> languages, List<CandidateProfilePreference> preferences) {
        return new CandidateProfileAggregate(
                null, "primary", "Backend Engineer", "Senior", 6,
                null, null, null, null, null, null,
                null, false, null,
                skills, languages, preferences, 0L);
    }

    private CandidateProfileAggregate profileWithKey(String profileKey) {
        return new CandidateProfileAggregate(
                null, profileKey, "Backend Engineer", "Senior", 6,
                null, null, null, null, null, null,
                null, false, null,
                skills, languages, preferences, 0L);
    }
}
