package com.darya.jobassistant.personalprojects.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PersonalProjectTechnologyTest {

    @Test
    void constructor_validTechnology_isCreated() {
        PersonalProjectTechnology technology = new PersonalProjectTechnology("Kafka", "Messaging", 0);

        assertThat(technology.name()).isEqualTo("Kafka");
        assertThat(technology.category()).isEqualTo("Messaging");
    }

    @Test
    void constructor_blankCategory_becomesNull() {
        PersonalProjectTechnology technology = new PersonalProjectTechnology("Kafka", "   ", 0);

        assertThat(technology.category()).isNull();
    }

    @Test
    void constructor_blankName_isRejected() {
        assertThatThrownBy(() -> new PersonalProjectTechnology("   ", null, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_negativeDisplayOrder_isRejected() {
        assertThatThrownBy(() -> new PersonalProjectTechnology("Kafka", null, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
