package com.darya.jobassistant.candidates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CandidateProfileTest {

    private final CandidatePreferences preferences = new CandidatePreferences(
            "Poland", "Remote", PreferenceImportance.STRONG, List.of("Poland"), false,
            List.of("B2B"), PreferenceImportance.PREFERRED, "Product", PreferenceImportance.PREFERRED, null);

    private final List<CandidateSkill> skills = List.of(
            new CandidateSkill("Java", SkillProficiency.STRONG, null),
            new CandidateSkill("Spring Boot", SkillProficiency.WORKING, null));

    @Test
    void constructor_validRichProfile_isCreated() {
        CandidateProfile profile = new CandidateProfile(
                "Senior Java Backend Engineer", "Senior", skills, List.of("English"), 6, preferences);

        assertThat(profile.targetRole()).isEqualTo("Senior Java Backend Engineer");
        assertThat(profile.targetSeniority()).isEqualTo("Senior");
        assertThat(profile.skills()).containsExactlyElementsOf(skills);
        assertThat(profile.languages()).containsExactly("English");
        assertThat(profile.experienceYears()).isEqualTo(6);
        assertThat(profile.preferences()).isSameAs(preferences);
    }

    @Test
    void constructor_nullSkillsAndLanguages_becomeEmptyLists() {
        CandidateProfile profile = new CandidateProfile("Senior Java Backend Engineer", "Senior", null, null, 6, preferences);

        assertThat(profile.skills()).isEmpty();
        assertThat(profile.languages()).isEmpty();
    }

    @Test
    void constructor_mutatingSourceLists_doesNotAffectStoredState() {
        List<CandidateSkill> mutableSkills = new ArrayList<>(skills);
        List<String> mutableLanguages = new ArrayList<>(List.of("English"));

        CandidateProfile profile = new CandidateProfile(
                "Senior Java Backend Engineer", "Senior", mutableSkills, mutableLanguages, 6, preferences);

        mutableSkills.add(new CandidateSkill("Kafka", SkillProficiency.BASIC, null));
        mutableLanguages.add("Polish");

        assertThat(profile.skills()).hasSize(2);
        assertThat(profile.languages()).containsExactly("English");
    }

    @Test
    void accessors_skillsAndLanguages_areUnmodifiable() {
        CandidateProfile profile = new CandidateProfile(
                "Senior Java Backend Engineer", "Senior", skills, List.of("English"), 6, preferences);

        assertThatThrownBy(() -> profile.skills().add(new CandidateSkill("Kafka", SkillProficiency.BASIC, null)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> profile.languages().add("Polish")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void constructor_negativeExperienceYears_isRejected() {
        assertThatThrownBy(() -> new CandidateProfile("Senior Java Backend Engineer", "Senior", skills, List.of(), -1, preferences))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_blankTargetRole_isRejected() {
        assertThatThrownBy(() -> new CandidateProfile("   ", "Senior", skills, List.of(), 6, preferences))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullTargetRole_isRejected() {
        assertThatThrownBy(() -> new CandidateProfile(null, "Senior", skills, List.of(), 6, preferences))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_blankTargetSeniority_isRejected() {
        assertThatThrownBy(() -> new CandidateProfile("Senior Java Backend Engineer", "   ", skills, List.of(), 6, preferences))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullTargetSeniority_isRejected() {
        assertThatThrownBy(() -> new CandidateProfile("Senior Java Backend Engineer", null, skills, List.of(), 6, preferences))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullPreferences_isRejected() {
        assertThatThrownBy(() -> new CandidateProfile("Senior Java Backend Engineer", "Senior", skills, List.of(), 6, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
