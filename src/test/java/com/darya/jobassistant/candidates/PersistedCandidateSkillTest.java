package com.darya.jobassistant.candidates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PersistedCandidateSkillTest {

    @Test
    void constructor_validSkill_isCreated() {
        PersistedCandidateSkill skill = new PersistedCandidateSkill("Java", "Language", SkillProficiency.EXPERT);

        assertThat(skill.name()).isEqualTo("Java");
        assertThat(skill.category()).isEqualTo("Language");
        assertThat(skill.proficiency()).isEqualTo(SkillProficiency.EXPERT);
    }

    @Test
    void constructor_nameIsTrimmed() {
        PersistedCandidateSkill skill = new PersistedCandidateSkill("  Kafka  ", null, SkillProficiency.STRONG);

        assertThat(skill.name()).isEqualTo("Kafka");
    }

    @Test
    void constructor_nullName_isRejected() {
        assertThatThrownBy(() -> new PersistedCandidateSkill(null, null, SkillProficiency.WORKING))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_blankName_isRejected() {
        assertThatThrownBy(() -> new PersistedCandidateSkill("   ", null, SkillProficiency.WORKING))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullProficiency_isRejected() {
        assertThatThrownBy(() -> new PersistedCandidateSkill("Java", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_blankCategory_becomesNull() {
        PersistedCandidateSkill skill = new PersistedCandidateSkill("Redis", "   ", SkillProficiency.BASIC);

        assertThat(skill.category()).isNull();
    }

    @Test
    void constructor_categoryIsTrimmed() {
        PersistedCandidateSkill skill = new PersistedCandidateSkill("Redis", "  Datastore  ", SkillProficiency.BASIC);

        assertThat(skill.category()).isEqualTo("Datastore");
    }

    @Test
    void onlySupportedProficiencyValuesExist_noneIsAbsent() {
        assertThat(SkillProficiency.values()).containsExactlyInAnyOrder(
                SkillProficiency.BASIC, SkillProficiency.WORKING, SkillProficiency.STRONG, SkillProficiency.EXPERT);
        assertThatThrownBy(() -> SkillProficiency.valueOf("NONE")).isInstanceOf(IllegalArgumentException.class);
    }
}
