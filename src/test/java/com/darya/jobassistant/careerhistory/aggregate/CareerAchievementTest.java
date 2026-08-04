package com.darya.jobassistant.careerhistory.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CareerAchievementTest {

    @Test
    void constructor_blankText_isRejected() {
        assertThatThrownBy(() -> new CareerAchievement(null, "   ", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_negativeDisplayOrder_isRejected() {
        assertThatThrownBy(() -> new CareerAchievement(null, "Reduced synthetic processing latency by 20%", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Qualitative achievements (no numeric metric) are valid - nothing requires one. */
    @Test
    void constructor_qualitativeAchievement_isAccepted() {
        CareerAchievement achievement = new CareerAchievement(null, "Mentored two engineers into senior roles", 0);

        assertThat(achievement.text()).isEqualTo("Mentored two engineers into senior roles");
    }
}
