package com.darya.jobassistant.careerhistory.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CareerResponsibilityTest {

    @Test
    void constructor_blankText_isRejected() {
        assertThatThrownBy(() -> new CareerResponsibility(null, "   ", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_negativeDisplayOrder_isRejected() {
        assertThatThrownBy(() -> new CareerResponsibility(null, "Own the billing service's reliability", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_textIsTrimmed_notOtherwiseAltered() {
        CareerResponsibility responsibility = new CareerResponsibility(null, "  Own the billing service's reliability  ", 0);

        assertThat(responsibility.text()).isEqualTo("Own the billing service's reliability");
    }
}
