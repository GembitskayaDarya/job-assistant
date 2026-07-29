package com.darya.jobassistant.jobdiscovery.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JobDiscoveryPropertiesTest {

    private static final JobDiscoveryProperties.Execution VALID_EXECUTION =
            new JobDiscoveryProperties.Execution(3, 5, 5, 30, 50);

    @Test
    void validEnabledConfiguration_isAccepted() {
        assertThatCode(() -> new JobDiscoveryProperties(true, VALID_EXECUTION)).doesNotThrowAnyException();
    }

    @Test
    void disabled_doesNotRequireExecutionToBeValid() {
        assertThatCode(() -> new JobDiscoveryProperties(false, null)).doesNotThrowAnyException();
        assertThatCode(() -> new JobDiscoveryProperties(false, new JobDiscoveryProperties.Execution(0, 0, 0, 0, -1)))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_rejectsNullExecution() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsNonPositiveMaxQueriesPerRun() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, new JobDiscoveryProperties.Execution(0, 5, 5, 30, 50)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsMaxQueriesPerRunAboveTen() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, new JobDiscoveryProperties.Execution(11, 50, 50, 30, 50)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_acceptsMaxQueriesPerRunBoundaries() {
        assertThatCode(() -> new JobDiscoveryProperties(true, new JobDiscoveryProperties.Execution(1, 5, 5, 30, 50)))
                .doesNotThrowAnyException();
        assertThatCode(() -> new JobDiscoveryProperties(true, new JobDiscoveryProperties.Execution(10, 50, 50, 30, 50)))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_rejectsNonPositiveMaxScrapesPerRun() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, new JobDiscoveryProperties.Execution(3, 0, 0, 30, 50)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsMaxScrapesPerRunAboveFifty() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, new JobDiscoveryProperties.Execution(3, 51, 5, 30, 50)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsNonPositiveMaxExtractionsPerRun() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, new JobDiscoveryProperties.Execution(3, 5, 0, 30, 50)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsMaxExtractionsPerRunAboveFifty() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, new JobDiscoveryProperties.Execution(3, 51, 51, 30, 50)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsMaxExtractionsPerRunAboveMaxScrapesPerRun() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, new JobDiscoveryProperties.Execution(3, 5, 6, 30, 50)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_acceptsMaxExtractionsPerRunEqualToMaxScrapesPerRun() {
        assertThatCode(() -> new JobDiscoveryProperties(true, new JobDiscoveryProperties.Execution(3, 5, 5, 30, 50)))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_rejectsNonPositiveMaxUniqueReferencesPerRun() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, new JobDiscoveryProperties.Execution(3, 5, 5, 0, 50)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsMaxUniqueReferencesPerRunAboveFiveHundred() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, new JobDiscoveryProperties.Execution(3, 5, 5, 501, 50)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsNegativeMaxReportedIssues() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, new JobDiscoveryProperties.Execution(3, 5, 5, 30, -1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_acceptsZeroMaxReportedIssues() {
        assertThatCode(() -> new JobDiscoveryProperties(true, new JobDiscoveryProperties.Execution(3, 5, 5, 30, 0)))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_rejectsMaxReportedIssuesAboveOneThousand() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, new JobDiscoveryProperties.Execution(3, 5, 5, 30, 1001)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_acceptsMaxReportedIssuesUpperBound() {
        assertThatCode(() -> new JobDiscoveryProperties(true, new JobDiscoveryProperties.Execution(3, 5, 5, 30, 1000)))
                .doesNotThrowAnyException();
    }
}
