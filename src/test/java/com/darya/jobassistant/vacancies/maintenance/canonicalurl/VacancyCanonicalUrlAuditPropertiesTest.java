package com.darya.jobassistant.vacancies.maintenance.canonicalurl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VacancyCanonicalUrlAuditPropertiesTest {

    @Test
    void disabled_startsWithoutAnyOtherValidValue() {
        assertThatCode(() -> new VacancyCanonicalUrlAuditProperties(false, 0, -1))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_acceptsValidConfiguration() {
        assertThatCode(() -> new VacancyCanonicalUrlAuditProperties(true, 500, 100))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_acceptsZeroMaxReportedIssues() {
        assertThatCode(() -> new VacancyCanonicalUrlAuditProperties(true, 500, 0))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_rejectsZeroBatchSize() {
        assertThatThrownBy(() -> new VacancyCanonicalUrlAuditProperties(true, 0, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsNegativeBatchSize() {
        assertThatThrownBy(() -> new VacancyCanonicalUrlAuditProperties(true, -1, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsBatchSizeAboveUpperBound() {
        assertThatThrownBy(() -> new VacancyCanonicalUrlAuditProperties(true, 5001, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsNegativeMaxReportedIssues() {
        assertThatThrownBy(() -> new VacancyCanonicalUrlAuditProperties(true, 500, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsMaxReportedIssuesAboveUpperBound() {
        assertThatThrownBy(() -> new VacancyCanonicalUrlAuditProperties(true, 500, 10_001))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
