package com.darya.jobassistant.candidates.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.candidates.SkillProficiency;
import org.junit.jupiter.api.Test;

class CandidateSkillTest {

    @Test
    void constructor_validSkill_isCreated() {
        CandidateSkill skill = new CandidateSkill("Java", "Language", "10+ years commercial use", SkillProficiency.EXPERT);

        assertThat(skill.name()).isEqualTo("Java");
        assertThat(skill.category()).isEqualTo("Language");
        assertThat(skill.note()).isEqualTo("10+ years commercial use");
        assertThat(skill.proficiency()).isEqualTo(SkillProficiency.EXPERT);
    }

    @Test
    void constructor_nameIsTrimmed() {
        CandidateSkill skill = new CandidateSkill("  Kafka  ", null, null, SkillProficiency.STRONG);

        assertThat(skill.name()).isEqualTo("Kafka");
    }

    @Test
    void constructor_nullName_isRejected() {
        assertThatThrownBy(() -> new CandidateSkill(null, null, null, SkillProficiency.WORKING))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_blankName_isRejected() {
        assertThatThrownBy(() -> new CandidateSkill("   ", null, null, SkillProficiency.WORKING))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullProficiency_isRejected() {
        assertThatThrownBy(() -> new CandidateSkill("Java", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_blankCategory_becomesNull() {
        CandidateSkill skill = new CandidateSkill("Redis", "   ", null, SkillProficiency.BASIC);

        assertThat(skill.category()).isNull();
    }

    @Test
    void constructor_categoryIsTrimmed() {
        CandidateSkill skill = new CandidateSkill("Redis", "  Datastore  ", null, SkillProficiency.BASIC);

        assertThat(skill.category()).isEqualTo("Datastore");
    }

    @Test
    void constructor_blankNote_becomesNull() {
        CandidateSkill skill = new CandidateSkill("Redis", null, "   ", SkillProficiency.BASIC);

        assertThat(skill.note()).isNull();
    }

    @Test
    void constructor_noteIsTrimmed() {
        CandidateSkill skill = new CandidateSkill("Redis", null, "  used for caching  ", SkillProficiency.BASIC);

        assertThat(skill.note()).isEqualTo("used for caching");
    }

    @Test
    void onlySupportedProficiencyValuesExist_noneIsAbsent() {
        assertThat(SkillProficiency.values()).containsExactlyInAnyOrder(
                SkillProficiency.BASIC, SkillProficiency.WORKING, SkillProficiency.STRONG, SkillProficiency.EXPERT);
        assertThatThrownBy(() -> SkillProficiency.valueOf("NONE")).isInstanceOf(IllegalArgumentException.class);
    }
}
