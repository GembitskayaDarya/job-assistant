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
            new CandidateLanguage("en", "FLUENT", 0));

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
        mutableLanguages.add(new CandidateLanguage("pl", null, 1));
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
        assertThatThrownBy(() -> profile.languages().add(new CandidateLanguage("ru", null, 1)))
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
                new CandidateLanguage("en", "FLUENT", 0),
                new CandidateLanguage("en", "NATIVE", 1));

        assertThatThrownBy(() -> profileWith(skills, duplicateLanguages, List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_duplicateLanguageDisplayOrder_isRejected() {
        List<CandidateLanguage> duplicateOrders = List.of(
                new CandidateLanguage("en", "FLUENT", 0),
                new CandidateLanguage("pl", "NATIVE", 0));

        assertThatThrownBy(() -> profileWith(skills, duplicateOrders, List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_languagesSortedByDisplayOrder_regardlessOfInputOrder() {
        List<CandidateLanguage> outOfOrder = List.of(
                new CandidateLanguage("pl", null, 1),
                new CandidateLanguage("en", null, 0));

        CandidateProfileAggregate profile = profileWith(skills, outOfOrder, List.of());

        assertThat(profile.languages()).extracting(CandidateLanguage::languageCode).containsExactly("en", "pl");
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

    // ---- Sprint 11 Step 5: CV header/contact facts + education ----

    @Test
    void constructor_headerFieldsAndEducation_arePopulated() {
        CandidateEducation education = new CandidateEducation("Example University", "BSc", "CS", null, null, null, null, 0);

        CandidateProfileAggregate profile = new CandidateProfileAggregate(
                null, "primary", "Backend Engineer", "Senior", 6,
                null, null, null, null, null, null, null, false, null,
                "person@example.com", "+48123456789", "https://www.linkedin.com/in/example", "Warsaw, Poland",
                "Senior Java Backend Engineer",
                skills, languages, preferences, List.of(education), 0L);

        assertThat(profile.email()).isEqualTo("person@example.com");
        assertThat(profile.phone()).isEqualTo("+48123456789");
        assertThat(profile.linkedinUrl()).isEqualTo("https://www.linkedin.com/in/example");
        assertThat(profile.cvLocation()).isEqualTo("Warsaw, Poland");
        assertThat(profile.cvHeadline()).isEqualTo("Senior Java Backend Engineer");
        assertThat(profile.education()).containsExactly(education);
    }

    /** Acceptance correction. */
    @Test
    void constructor_fullName_isPopulated() {
        CandidateProfileAggregate profile = new CandidateProfileAggregate(
                null, "primary", "Backend Engineer", "Senior", 6,
                null, null, null, null, null, null, null, false, null,
                "Jane Doe", null, null, null, null, null,
                skills, languages, preferences, List.of(), 0L);

        assertThat(profile.fullName()).isEqualTo("Jane Doe");
    }

    @Test
    void constructor_blankFullName_becomesNull() {
        CandidateProfileAggregate profile = new CandidateProfileAggregate(
                null, "primary", "Backend Engineer", "Senior", 6,
                null, null, null, null, null, null, null, false, null,
                "   ", null, null, null, null, null,
                skills, languages, preferences, List.of(), 0L);

        assertThat(profile.fullName()).isNull();
    }

    @Test
    void constructor_oldEighteenArgOverload_defaultsHeaderFieldsAndEducation() {
        CandidateProfileAggregate profile = profileWith(skills, languages, preferences);

        assertThat(profile.fullName()).isNull();
        assertThat(profile.email()).isNull();
        assertThat(profile.phone()).isNull();
        assertThat(profile.linkedinUrl()).isNull();
        assertThat(profile.cvLocation()).isNull();
        assertThat(profile.cvHeadline()).isNull();
        assertThat(profile.education()).isEmpty();
    }

    @Test
    void constructor_oldTwentyFourArgOverload_defaultsFullNameOnly() {
        CandidateProfileAggregate profile = new CandidateProfileAggregate(
                null, "primary", "Backend Engineer", "Senior", 6,
                null, null, null, null, null, null, null, false, null,
                "person@example.com", "+48123456789", "https://www.linkedin.com/in/example", "Warsaw, Poland",
                "Senior Java Backend Engineer",
                skills, languages, preferences, List.of(), 0L);

        assertThat(profile.fullName()).isNull();
        assertThat(profile.email()).isEqualTo("person@example.com");
    }

    @Test
    void constructor_invalidEmail_isRejected() {
        assertThatThrownBy(() -> new CandidateProfileAggregate(
                null, "primary", "Backend Engineer", "Senior", 6,
                null, null, null, null, null, null, null, false, null,
                "not-an-email", null, null, null, null,
                skills, languages, preferences, List.of(), 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_invalidLinkedinUrl_isRejected() {
        assertThatThrownBy(() -> new CandidateProfileAggregate(
                null, "primary", "Backend Engineer", "Senior", 6,
                null, null, null, null, null, null, null, false, null,
                null, null, "https://example.com/not-linkedin", null, null,
                skills, languages, preferences, List.of(), 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_linkedinUrlWithSubdomain_isAccepted() {
        CandidateProfileAggregate profile = new CandidateProfileAggregate(
                null, "primary", "Backend Engineer", "Senior", 6,
                null, null, null, null, null, null, null, false, null,
                null, null, "https://pl.linkedin.com/in/example", null, null,
                skills, languages, preferences, List.of(), 0L);

        assertThat(profile.linkedinUrl()).isEqualTo("https://pl.linkedin.com/in/example");
    }

    @Test
    void constructor_blankHeaderFields_becomeNull() {
        CandidateProfileAggregate profile = new CandidateProfileAggregate(
                null, "primary", "Backend Engineer", "Senior", 6,
                null, null, null, null, null, null, null, false, null,
                "  ", "  ", null, "  ", "  ",
                skills, languages, preferences, List.of(), 0L);

        assertThat(profile.email()).isNull();
        assertThat(profile.phone()).isNull();
        assertThat(profile.cvLocation()).isNull();
        assertThat(profile.cvHeadline()).isNull();
    }

    @Test
    void constructor_educationSortedByDisplayOrder_regardlessOfInputOrder() {
        CandidateEducation second = new CandidateEducation("University B", null, null, null, null, null, null, 1);
        CandidateEducation first = new CandidateEducation("University A", null, null, null, null, null, null, 0);

        CandidateProfileAggregate profile = new CandidateProfileAggregate(
                null, "primary", "Backend Engineer", "Senior", 6,
                null, null, null, null, null, null, null, false, null,
                null, null, null, null, null,
                skills, languages, preferences, List.of(second, first), 0L);

        assertThat(profile.education()).extracting(CandidateEducation::institution)
                .containsExactly("University A", "University B");
    }

    @Test
    void constructor_duplicateEducationDisplayOrder_isRejected() {
        CandidateEducation first = new CandidateEducation("University A", null, null, null, null, null, null, 0);
        CandidateEducation second = new CandidateEducation("University B", null, null, null, null, null, null, 0);

        assertThatThrownBy(() -> new CandidateProfileAggregate(
                null, "primary", "Backend Engineer", "Senior", 6,
                null, null, null, null, null, null, null, false, null,
                null, null, null, null, null,
                skills, languages, preferences, List.of(first, second), 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private CandidateProfileAggregate profileWithKey(String profileKey) {
        return new CandidateProfileAggregate(
                null, profileKey, "Backend Engineer", "Senior", 6,
                null, null, null, null, null, null,
                null, false, null,
                skills, languages, preferences, 0L);
    }
}
