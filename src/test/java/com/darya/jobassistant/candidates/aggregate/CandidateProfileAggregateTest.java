package com.darya.jobassistant.candidates.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.candidates.SkillProficiency;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CandidateProfileAggregateTest {

    private final List<CandidateSkill> skills = List.of(
            new CandidateSkill("Java", "Language", SkillProficiency.EXPERT),
            new CandidateSkill("Spring Boot", "Framework", SkillProficiency.STRONG));

    private final List<CandidateLanguage> languages = List.of(
            new CandidateLanguage("en", "FLUENT"));

    @Test
    void constructor_validProfile_isCreated() {
        UUID id = UUID.randomUUID();

        CandidateProfileAggregate profile = new CandidateProfileAggregate(
                id, "primary", "Senior Java Backend Engineer", "Senior", 6,
                "Product", "Europe", "B2B", "REMOTE", "EUR", new BigDecimal("8000.00"),
                skills, languages, 0L);

        assertThat(profile.id()).isEqualTo(id);
        assertThat(profile.profileKey()).isEqualTo("primary");
        assertThat(profile.targetRole()).isEqualTo("Senior Java Backend Engineer");
        assertThat(profile.seniority()).isEqualTo("Senior");
        assertThat(profile.experienceYears()).isEqualTo(6);
        assertThat(profile.preferredCompanyType()).isEqualTo("Product");
        assertThat(profile.preferredLocation()).isEqualTo("Europe");
        assertThat(profile.employmentModel()).isEqualTo("B2B");
        assertThat(profile.remotePolicy()).isEqualTo("REMOTE");
        assertThat(profile.salaryCurrency()).isEqualTo("EUR");
        assertThat(profile.minimumSalary()).isEqualByComparingTo("8000.00");
        assertThat(profile.skills()).containsExactlyElementsOf(skills);
        assertThat(profile.languages()).containsExactlyElementsOf(languages);
        assertThat(profile.version()).isZero();
    }

    @Test
    void constructor_nullIdAndOptionalFields_areAllowed_representingANotYetPersistedProfile() {
        CandidateProfileAggregate profile = new CandidateProfileAggregate(
                null, "primary", "Backend Engineer", null, 0,
                null, null, null, null, null, null,
                null, null, 0L);

        assertThat(profile.id()).isNull();
        assertThat(profile.skills()).isEmpty();
        assertThat(profile.languages()).isEmpty();
    }

    @Test
    void constructor_mutatingSourceLists_doesNotAffectStoredState() {
        List<CandidateSkill> mutableSkills = new ArrayList<>(skills);
        List<CandidateLanguage> mutableLanguages = new ArrayList<>(languages);

        CandidateProfileAggregate profile = profileWith(mutableSkills, mutableLanguages);

        mutableSkills.add(new CandidateSkill("Kafka", null, SkillProficiency.BASIC));
        mutableLanguages.add(new CandidateLanguage("pl", null));

        assertThat(profile.skills()).hasSize(2);
        assertThat(profile.languages()).hasSize(1);
    }

    @Test
    void accessors_skillsAndLanguages_areUnmodifiable() {
        CandidateProfileAggregate profile = profileWith(skills, languages);

        assertThatThrownBy(() -> profile.skills().add(new CandidateSkill("Kotlin", null, SkillProficiency.BASIC)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> profile.languages().add(new CandidateLanguage("ru", null)))
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
                null, "primary", "   ", null, 0, null, null, null, null, null, null, skills, languages, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_negativeExperienceYears_isRejected() {
        assertThatThrownBy(() -> new CandidateProfileAggregate(
                null, "primary", "Backend Engineer", null, -1, null, null, null, null, null, null, skills, languages, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_negativeMinimumSalary_isRejected() {
        assertThatThrownBy(() -> new CandidateProfileAggregate(
                null, "primary", "Backend Engineer", null, 0, null, null, null, null, null,
                new BigDecimal("-1.00"), skills, languages, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_negativeVersion_isRejected() {
        assertThatThrownBy(() -> new CandidateProfileAggregate(
                null, "primary", "Backend Engineer", null, 0, null, null, null, null, null, null,
                skills, languages, -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_duplicateSkillNames_areRejected() {
        List<CandidateSkill> duplicateSkills = List.of(
                new CandidateSkill("Java", null, SkillProficiency.EXPERT),
                new CandidateSkill("Java", null, SkillProficiency.BASIC));

        assertThatThrownBy(() -> profileWith(duplicateSkills, languages)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_duplicateLanguageCodes_areRejected() {
        List<CandidateLanguage> duplicateLanguages = List.of(
                new CandidateLanguage("en", "FLUENT"),
                new CandidateLanguage("en", "NATIVE"));

        assertThatThrownBy(() -> profileWith(skills, duplicateLanguages)).isInstanceOf(IllegalArgumentException.class);
    }

    private CandidateProfileAggregate profileWith(List<CandidateSkill> skills, List<CandidateLanguage> languages) {
        return new CandidateProfileAggregate(
                null, "primary", "Backend Engineer", "Senior", 6,
                null, null, null, null, null, null,
                skills, languages, 0L);
    }

    private CandidateProfileAggregate profileWithKey(String profileKey) {
        return new CandidateProfileAggregate(
                null, profileKey, "Backend Engineer", "Senior", 6,
                null, null, null, null, null, null,
                skills, languages, 0L);
    }
}
