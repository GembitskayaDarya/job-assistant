package com.darya.jobassistant.personalprojects.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersonalProjectHighlightTest {

    @Test
    void constructor_validHighlight_isCreated() {
        PersonalProjectHighlight highlight = new PersonalProjectHighlight(UUID.randomUUID(), "Built a REST API", 0);

        assertThat(highlight.text()).isEqualTo("Built a REST API");
        assertThat(highlight.displayOrder()).isZero();
    }

    @Test
    void constructor_notYetPersistedConvenienceConstructor_hasNullId() {
        PersonalProjectHighlight highlight = new PersonalProjectHighlight("Built a REST API", 0);

        assertThat(highlight.id()).isNull();
    }

    @Test
    void constructor_blankText_isRejected() {
        assertThatThrownBy(() -> new PersonalProjectHighlight("   ", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_negativeDisplayOrder_isRejected() {
        assertThatThrownBy(() -> new PersonalProjectHighlight("text", -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
