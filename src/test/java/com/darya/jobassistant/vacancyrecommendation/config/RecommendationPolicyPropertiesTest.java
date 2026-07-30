package com.darya.jobassistant.vacancyrecommendation.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RecommendationPolicyPropertiesTest {

    @Test
    void acceptsValidScore() {
        assertThatCode(() -> new RecommendationPolicyProperties(70)).doesNotThrowAnyException();
    }

    @Test
    void acceptsBoundaries() {
        assertThatCode(() -> new RecommendationPolicyProperties(0)).doesNotThrowAnyException();
        assertThatCode(() -> new RecommendationPolicyProperties(100)).doesNotThrowAnyException();
    }

    @Test
    void rejectsScoreBelowZero() {
        assertThatThrownBy(() -> new RecommendationPolicyProperties(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsScoreAbove100() {
        assertThatThrownBy(() -> new RecommendationPolicyProperties(101)).isInstanceOf(IllegalArgumentException.class);
    }
}
