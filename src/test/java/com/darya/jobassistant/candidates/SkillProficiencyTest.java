package com.darya.jobassistant.candidates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SkillProficiencyTest {

    @Test
    void values_containsOnlyTheFourCalibratedLevels() {
        assertThat(SkillProficiency.values())
                .containsExactlyInAnyOrder(
                        SkillProficiency.BASIC,
                        SkillProficiency.WORKING,
                        SkillProficiency.STRONG,
                        SkillProficiency.EXPERT);
    }

    @Test
    void values_neverContainNone() {
        assertThat(Arrays.stream(SkillProficiency.values()).map(Enum::name)).doesNotContain("NONE");
    }

    @Test
    void valueOf_none_isRejected() {
        assertThatThrownBy(() -> SkillProficiency.valueOf("NONE"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
