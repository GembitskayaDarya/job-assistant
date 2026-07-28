package com.darya.jobassistant.vacancies.maintenance.canonicalurl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VacancyCanonicalUrlBackfillPropertiesTest {

    @Test
    void disabled_startsWithoutAnyOtherValidValue() {
        assertThatCode(() -> new VacancyCanonicalUrlBackfillProperties(false, null, 0))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_acceptsValidDryRunConfiguration() {
        assertThatCode(() -> new VacancyCanonicalUrlBackfillProperties(true, VacancyCanonicalUrlBackfillMode.DRY_RUN, 500))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_acceptsValidApplyConfiguration() {
        assertThatCode(() -> new VacancyCanonicalUrlBackfillProperties(true, VacancyCanonicalUrlBackfillMode.APPLY, 500))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_rejectsNullMode() {
        assertThatThrownBy(() -> new VacancyCanonicalUrlBackfillProperties(true, null, 500))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsZeroBatchSize() {
        assertThatThrownBy(() -> new VacancyCanonicalUrlBackfillProperties(true, VacancyCanonicalUrlBackfillMode.DRY_RUN, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsNegativeBatchSize() {
        assertThatThrownBy(() -> new VacancyCanonicalUrlBackfillProperties(true, VacancyCanonicalUrlBackfillMode.DRY_RUN, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsBatchSizeAboveUpperBound() {
        assertThatThrownBy(() -> new VacancyCanonicalUrlBackfillProperties(true, VacancyCanonicalUrlBackfillMode.DRY_RUN, 5001))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
