package com.darya.jobassistant.candidates.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.candidates.SkillProficiency;
import org.junit.jupiter.api.Test;

class CandidateSkillTest {

    @Test
    void constructor_validSkill_isCreated() {
        CandidateSkill skill = new CandidateSkill("Java", "Language", SkillProficiency.EXPERT);

        assertThat(skill.name()).isEqualTo("Java");
        assertThat(skill.category()).isEqualTo("Language");
        assertThat(skill.proficiency()).isEqualTo(SkillProficiency.EXPERT);
    }

    @Test
    void constructor_nameIsTrimmed() {
        CandidateSkill skill = new CandidateSkill("  Kafka  ", null, SkillProficiency.STRONG);

        assertThat(skill.name()).isEqualTo("Kafka");
    }

    @Test
    void constructor_nullName_isRejected() {
        assertThatThrownBy(() -> new CandidateSkill(null, null, SkillProficiency.WORKING))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_blankName_isRejected() {
        assertThatThrownBy(() -> new CandidateSkill("   ", null, SkillProficiency.WORKING))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullProficiency_isRejected() {
        assertThatThrownBy(() -> new CandidateSkill("Java", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_blankCategory_becomesNull() {
        CandidateSkill skill = new CandidateSkill("Redis", "   ", SkillProficiency.BASIC);

        assertThat(skill.category()).isNull();
    }

    @Test
    void constructor_categoryIsTrimmed() {
        CandidateSkill skill = new CandidateSkill("Redis", "  Datastore  ", SkillProficiency.BASIC);

        assertThat(skill.category()).isEqualTo("Datastore");
    }

    @Test
    void onlySupportedProficiencyValuesExist_noneIsAbsent() {
        assertThat(SkillProficiency.values()).containsExactlyInAnyOrder(
                SkillProficiency.BASIC, SkillProficiency.WORKING, SkillProficiency.STRONG, SkillProficiency.EXPERT);
        assertThatThrownBy(() -> SkillProficiency.valueOf("NONE")).isInstanceOf(IllegalArgumentException.class);
    }
}
