package com.darya.jobassistant.jobdiscovery.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JobDiscoveryPropertiesTest {

    private static final JobDiscoveryProperties.Execution VALID_EXECUTION =
            new JobDiscoveryProperties.Execution(3, 5, 5, 30, 50);
    private static final JobDiscoveryProperties.Budget VALID_BUDGET =
            new JobDiscoveryProperties.Budget(800, 200, 15);

    @Test
    void validEnabledConfiguration_isAccepted() {
        assertThatCode(() -> new JobDiscoveryProperties(true, VALID_EXECUTION, VALID_BUDGET)).doesNotThrowAnyException();
    }

    @Test
    void disabled_doesNotRequireExecutionOrBudgetToBeValid() {
        assertThatCode(() -> new JobDiscoveryProperties(false, null, null)).doesNotThrowAnyException();
        assertThatCode(() -> new JobDiscoveryProperties(false,
                new JobDiscoveryProperties.Execution(0, 0, 0, 0, -1),
                new JobDiscoveryProperties.Budget(0, -1, 0)))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_rejectsNullExecution() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, null, VALID_BUDGET))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsNonPositiveMaxQueriesPerRun() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(0, 5, 5, 30, 50), VALID_BUDGET))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsMaxQueriesPerRunAboveTen() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(11, 50, 50, 30, 50), VALID_BUDGET))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_acceptsMaxQueriesPerRunBoundaries() {
        assertThatCode(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(1, 5, 5, 30, 50), VALID_BUDGET))
                .doesNotThrowAnyException();
        assertThatCode(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(10, 50, 50, 30, 50), VALID_BUDGET))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_rejectsNonPositiveMaxScrapesPerRun() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 0, 0, 30, 50), VALID_BUDGET))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsMaxScrapesPerRunAboveFifty() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 51, 5, 30, 50), VALID_BUDGET))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsNonPositiveMaxExtractionsPerRun() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 5, 0, 30, 50), VALID_BUDGET))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsMaxExtractionsPerRunAboveFifty() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 51, 51, 30, 50), VALID_BUDGET))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsMaxExtractionsPerRunAboveMaxScrapesPerRun() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 5, 6, 30, 50), VALID_BUDGET))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_acceptsMaxExtractionsPerRunEqualToMaxScrapesPerRun() {
        assertThatCode(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 5, 5, 30, 50), VALID_BUDGET))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_rejectsNonPositiveMaxUniqueReferencesPerRun() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 5, 5, 0, 50), VALID_BUDGET))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsMaxUniqueReferencesPerRunAboveFiveHundred() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 5, 5, 501, 50), VALID_BUDGET))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsNegativeMaxReportedIssues() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 5, 5, 30, -1), VALID_BUDGET))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_acceptsZeroMaxReportedIssues() {
        assertThatCode(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 5, 5, 30, 0), VALID_BUDGET))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_rejectsMaxReportedIssuesAboveOneThousand() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 5, 5, 30, 1001), VALID_BUDGET))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_acceptsMaxReportedIssuesUpperBound() {
        assertThatCode(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 5, 5, 30, 1000), VALID_BUDGET))
                .doesNotThrowAnyException();
    }

    // --- budget --------------------------------------------------------------------------------

    @Test
    void enabled_rejectsNullBudget() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, VALID_EXECUTION, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsNonPositiveMonthlyCreditLimit() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, VALID_EXECUTION,
                new JobDiscoveryProperties.Budget(0, 200, 15)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsMonthlyCreditLimitAboveOneMillion() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, VALID_EXECUTION,
                new JobDiscoveryProperties.Budget(1_000_001, 200, 15)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_acceptsMonthlyCreditLimitBoundaries() {
        assertThatCode(() -> new JobDiscoveryProperties(true, VALID_EXECUTION,
                new JobDiscoveryProperties.Budget(1, 0, 1)))
                .doesNotThrowAnyException();
        assertThatCode(() -> new JobDiscoveryProperties(true, VALID_EXECUTION,
                new JobDiscoveryProperties.Budget(1_000_000, 200, 15)))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_rejectsNegativeReserveCredits() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, VALID_EXECUTION,
                new JobDiscoveryProperties.Budget(800, -1, 15)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsReserveCreditsAboveOneMillion() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, VALID_EXECUTION,
                new JobDiscoveryProperties.Budget(1_000_000, 1_000_001, 15)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_acceptsReserveCreditsBoundaries() {
        assertThatCode(() -> new JobDiscoveryProperties(true, VALID_EXECUTION,
                new JobDiscoveryProperties.Budget(800, 0, 15)))
                .doesNotThrowAnyException();
        assertThatCode(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 5, 5, 30, 50),
                new JobDiscoveryProperties.Budget(1_000_000, 1_000_000, 15)))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_rejectsNonPositiveMaxEstimatedCreditsPerRun() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, VALID_EXECUTION,
                new JobDiscoveryProperties.Budget(800, 200, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsMaxEstimatedCreditsPerRunAboveOneHundredThousand() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, VALID_EXECUTION,
                new JobDiscoveryProperties.Budget(200_000, 200, 100_001)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_acceptsMaxEstimatedCreditsPerRunBoundaries() {
        assertThatCode(() -> new JobDiscoveryProperties(true, VALID_EXECUTION,
                new JobDiscoveryProperties.Budget(800, 200, 1)))
                .doesNotThrowAnyException();
        assertThatCode(() -> new JobDiscoveryProperties(true, VALID_EXECUTION,
                new JobDiscoveryProperties.Budget(100_000, 0, 100_000)))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_rejectsMaxEstimatedCreditsPerRunAboveMonthlyCreditLimit() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, VALID_EXECUTION,
                new JobDiscoveryProperties.Budget(10, 0, 15)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_acceptsMaxEstimatedCreditsPerRunEqualToMonthlyCreditLimit() {
        assertThatCode(() -> new JobDiscoveryProperties(true, VALID_EXECUTION,
                new JobDiscoveryProperties.Budget(15, 0, 15)))
                .doesNotThrowAnyException();
    }
}
