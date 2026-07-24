package com.darya.jobassistant.candidates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CandidateSkillTest {

    @Test
    void constructor_validSkill_isCreated() {
        CandidateSkill skill = new CandidateSkill("Java", SkillProficiency.EXPERT, "10+ years commercial use");

        assertThat(skill.name()).isEqualTo("Java");
        assertThat(skill.proficiency()).isEqualTo(SkillProficiency.EXPERT);
        assertThat(skill.note()).isEqualTo("10+ years commercial use");
    }

    @Test
    void constructor_nameIsTrimmed() {
        CandidateSkill skill = new CandidateSkill("  Kafka  ", SkillProficiency.STRONG, null);

        assertThat(skill.name()).isEqualTo("Kafka");
    }

    @Test
    void constructor_nullName_isRejected() {
        assertThatThrownBy(() -> new CandidateSkill(null, SkillProficiency.WORKING, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_blankName_isRejected() {
        assertThatThrownBy(() -> new CandidateSkill("   ", SkillProficiency.WORKING, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullProficiency_isRejected() {
        assertThatThrownBy(() -> new CandidateSkill("Java", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_blankNote_becomesNull() {
        CandidateSkill skill = new CandidateSkill("Redis", SkillProficiency.BASIC, "   ");

        assertThat(skill.note()).isNull();
    }

    @Test
    void constructor_nullNote_staysNull() {
        CandidateSkill skill = new CandidateSkill("Redis", SkillProficiency.BASIC, null);

        assertThat(skill.note()).isNull();
    }

    @Test
    void constructor_noteIsTrimmed() {
        CandidateSkill skill = new CandidateSkill("Redis", SkillProficiency.BASIC, "  used for caching  ");

        assertThat(skill.note()).isEqualTo("used for caching");
    }
}
