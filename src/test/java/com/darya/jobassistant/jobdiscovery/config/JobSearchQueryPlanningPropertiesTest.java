package com.darya.jobassistant.jobdiscovery.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JobSearchQueryPlanningPropertiesTest {

    @Test
    void validConfiguration_isAccepted() {
        assertThatCode(() -> new JobSearchQueryPlanningProperties(5, 10, 3)).doesNotThrowAnyException();
    }

    @Test
    void rejectsZeroMaxQueries() {
        assertThatThrownBy(() -> new JobSearchQueryPlanningProperties(0, 10, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeMaxQueries() {
        assertThatThrownBy(() -> new JobSearchQueryPlanningProperties(-1, 10, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsExcessiveMaxQueries() {
        assertThatThrownBy(() -> new JobSearchQueryPlanningProperties(21, 10, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsZeroResultsPerQuery() {
        assertThatThrownBy(() -> new JobSearchQueryPlanningProperties(5, 0, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeResultsPerQuery() {
        assertThatThrownBy(() -> new JobSearchQueryPlanningProperties(5, -1, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsExcessiveResultsPerQuery() {
        assertThatThrownBy(() -> new JobSearchQueryPlanningProperties(5, 51, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsZeroMaxSkillsPerQuery() {
        assertThatThrownBy(() -> new JobSearchQueryPlanningProperties(5, 10, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeMaxSkillsPerQuery() {
        assertThatThrownBy(() -> new JobSearchQueryPlanningProperties(5, 10, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsExcessiveMaxSkillsPerQuery() {
        assertThatThrownBy(() -> new JobSearchQueryPlanningProperties(5, 10, 11))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
