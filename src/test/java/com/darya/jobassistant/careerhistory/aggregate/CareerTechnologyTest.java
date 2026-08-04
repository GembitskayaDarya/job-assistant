package com.darya.jobassistant.careerhistory.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CareerTechnologyTest {

    @Test
    void constructor_blankName_isRejected() {
        assertThatThrownBy(() -> new CareerTechnology(null, "   ", null, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_negativeDisplayOrder_isRejected() {
        assertThatThrownBy(() -> new CareerTechnology(null, "Kafka", null, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_capitalization_isNeverAltered() {
        CareerTechnology technology = new CareerTechnology(null, "PostgreSQL", "Database", 0);

        assertThat(technology.name()).isEqualTo("PostgreSQL");
    }

    @Test
    void constructor_blankCategory_normalizesToNull() {
        CareerTechnology technology = new CareerTechnology(null, "Kafka", "   ", 0);

        assertThat(technology.category()).isNull();
    }
}
