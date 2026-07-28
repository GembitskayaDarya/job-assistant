package com.darya.jobassistant.integrations.jobsearch;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JobSearchRequestTest {

    @Test
    void validRequest_isAccepted() {
        assertThatCode(() -> new JobSearchRequest("java backend remote", 10)).doesNotThrowAnyException();
    }

    @Test
    void rejectsBlankQuery() {
        assertThatThrownBy(() -> new JobSearchRequest("   ", 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullQuery() {
        assertThatThrownBy(() -> new JobSearchRequest(null, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsZeroMaxResults() {
        assertThatThrownBy(() -> new JobSearchRequest("java backend", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeMaxResults() {
        assertThatThrownBy(() -> new JobSearchRequest("java backend", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
