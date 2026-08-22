package com.darya.jobassistant.jobdiscovery.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class JobDiscoveryPropertiesTest {

    private static final JobDiscoveryProperties.Execution VALID_EXECUTION =
            new JobDiscoveryProperties.Execution(3, 5, 5, 30, 50);
    private static final JobDiscoveryProperties.Budget VALID_BUDGET =
            new JobDiscoveryProperties.Budget(800, 200, 15);
    private static final JobDiscoveryProperties.Scheduler DISABLED_SCHEDULER =
            new JobDiscoveryProperties.Scheduler(false, "0 0 8 * * *", "Europe/Warsaw",
                    Duration.ofHours(2), Duration.ofMinutes(1));
    private static final JobDiscoveryProperties.Scheduler ENABLED_SCHEDULER =
            new JobDiscoveryProperties.Scheduler(true, "0 0 8 * * *", "Europe/Warsaw",
                    Duration.ofHours(2), Duration.ofMinutes(1));

    @Test
    void validEnabledConfiguration_isAccepted() {
        assertThatCode(() -> new JobDiscoveryProperties(true, VALID_EXECUTION, VALID_BUDGET, DISABLED_SCHEDULER))
                .doesNotThrowAnyException();
    }

    @Test
    void disabled_doesNotRequireExecutionOrBudgetToBeValid() {
        assertThatCode(() -> new JobDiscoveryProperties(false, null, null, null)).doesNotThrowAnyException();
        assertThatCode(() -> new JobDiscoveryProperties(false,
                new JobDiscoveryProperties.Execution(0, 0, 0, 0, -1),
                new JobDiscoveryProperties.Budget(0, -1, 0),
                null))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_rejectsNullExecution() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, null, VALID_BUDGET, DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsNonPositiveMaxQueriesPerRun() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(0, 5, 5, 30, 50), VALID_BUDGET, DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsMaxQueriesPerRunAboveTen() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(11, 50, 50, 30, 50), VALID_BUDGET, DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_acceptsMaxQueriesPerRunBoundaries() {
        assertThatCode(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(1, 5, 5, 30, 50), VALID_BUDGET, DISABLED_SCHEDULER))
                .doesNotThrowAnyException();
        assertThatCode(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(10, 50, 50, 30, 50), VALID_BUDGET, DISABLED_SCHEDULER))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_rejectsNonPositiveMaxScrapesPerRun() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 0, 0, 30, 50), VALID_BUDGET, DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsMaxScrapesPerRunAboveFifty() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 51, 5, 30, 50), VALID_BUDGET, DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsNonPositiveMaxExtractionsPerRun() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 5, 0, 30, 50), VALID_BUDGET, DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsMaxExtractionsPerRunAboveFifty() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 51, 51, 30, 50), VALID_BUDGET, DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsMaxExtractionsPerRunAboveMaxScrapesPerRun() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 5, 6, 30, 50), VALID_BUDGET, DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_acceptsMaxExtractionsPerRunEqualToMaxScrapesPerRun() {
        assertThatCode(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 5, 5, 30, 50), VALID_BUDGET, DISABLED_SCHEDULER))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_rejectsNonPositiveMaxUniqueReferencesPerRun() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 5, 5, 0, 50), VALID_BUDGET, DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsMaxUniqueReferencesPerRunAboveFiveHundred() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 5, 5, 501, 50), VALID_BUDGET, DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsNegativeMaxReportedIssues() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 5, 5, 30, -1), VALID_BUDGET, DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_acceptsZeroMaxReportedIssues() {
        assertThatCode(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 5, 5, 30, 0), VALID_BUDGET, DISABLED_SCHEDULER))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_rejectsMaxReportedIssuesAboveOneThousand() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 5, 5, 30, 1001), VALID_BUDGET, DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_acceptsMaxReportedIssuesUpperBound() {
        assertThatCode(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 5, 5, 30, 1000), VALID_BUDGET, DISABLED_SCHEDULER))
                .doesNotThrowAnyException();
    }

    // --- budget --------------------------------------------------------------------------------

    @Test
    void enabled_rejectsNullBudget() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, VALID_EXECUTION, null, DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsNonPositiveMonthlyCreditLimit() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, VALID_EXECUTION,
                new JobDiscoveryProperties.Budget(0, 200, 15), DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsMonthlyCreditLimitAboveOneMillion() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, VALID_EXECUTION,
                new JobDiscoveryProperties.Budget(1_000_001, 200, 15), DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_acceptsMonthlyCreditLimitBoundaries() {
        assertThatCode(() -> new JobDiscoveryProperties(true, VALID_EXECUTION,
                new JobDiscoveryProperties.Budget(1, 0, 1), DISABLED_SCHEDULER))
                .doesNotThrowAnyException();
        assertThatCode(() -> new JobDiscoveryProperties(true, VALID_EXECUTION,
                new JobDiscoveryProperties.Budget(1_000_000, 200, 15), DISABLED_SCHEDULER))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_rejectsNegativeReserveCredits() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, VALID_EXECUTION,
                new JobDiscoveryProperties.Budget(800, -1, 15), DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsReserveCreditsAboveOneMillion() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, VALID_EXECUTION,
                new JobDiscoveryProperties.Budget(1_000_000, 1_000_001, 15), DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_acceptsReserveCreditsBoundaries() {
        assertThatCode(() -> new JobDiscoveryProperties(true, VALID_EXECUTION,
                new JobDiscoveryProperties.Budget(800, 0, 15), DISABLED_SCHEDULER))
                .doesNotThrowAnyException();
        assertThatCode(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 5, 5, 30, 50),
                new JobDiscoveryProperties.Budget(1_000_000, 1_000_000, 15), DISABLED_SCHEDULER))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_rejectsNonPositiveMaxEstimatedCreditsPerRun() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, VALID_EXECUTION,
                new JobDiscoveryProperties.Budget(800, 200, 0), DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsMaxEstimatedCreditsPerRunAboveOneHundredThousand() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, VALID_EXECUTION,
                new JobDiscoveryProperties.Budget(200_000, 200, 100_001), DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_acceptsMaxEstimatedCreditsPerRunBoundaries() {
        assertThatCode(() -> new JobDiscoveryProperties(true, VALID_EXECUTION,
                new JobDiscoveryProperties.Budget(800, 200, 1), DISABLED_SCHEDULER))
                .doesNotThrowAnyException();
        assertThatCode(() -> new JobDiscoveryProperties(true, VALID_EXECUTION,
                new JobDiscoveryProperties.Budget(100_000, 0, 100_000), DISABLED_SCHEDULER))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_rejectsMaxEstimatedCreditsPerRunAboveMonthlyCreditLimit() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, VALID_EXECUTION,
                new JobDiscoveryProperties.Budget(10, 0, 15), DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_acceptsMaxEstimatedCreditsPerRunEqualToMonthlyCreditLimit() {
        assertThatCode(() -> new JobDiscoveryProperties(true, VALID_EXECUTION,
                new JobDiscoveryProperties.Budget(15, 0, 15), DISABLED_SCHEDULER))
                .doesNotThrowAnyException();
    }

    // --- scheduler -------------------------------------------------------------------------------

    @Test
    void schedulerDisabled_doesNotRequireItsOwnFieldsToBeValid() {
        JobDiscoveryProperties.Scheduler invalidButDisabled =
                new JobDiscoveryProperties.Scheduler(false, "", "not-a-zone", Duration.ZERO, Duration.ofDays(1));
        assertThatCode(() -> new JobDiscoveryProperties(true, VALID_EXECUTION, VALID_BUDGET, invalidButDisabled))
                .doesNotThrowAnyException();
    }

    @Test
    void schedulerNull_isAcceptedAsDisabled() {
        assertThatCode(() -> new JobDiscoveryProperties(true, VALID_EXECUTION, VALID_BUDGET, null))
                .doesNotThrowAnyException();
    }

    @Test
    void schedulerEnabled_validConfiguration_isAccepted() {
        assertThatCode(() -> new JobDiscoveryProperties(true, VALID_EXECUTION, VALID_BUDGET, ENABLED_SCHEDULER))
                .doesNotThrowAnyException();
    }

    @Test
    void schedulerEnabled_whileDiscoveryDisabled_failsClearly() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(false, VALID_EXECUTION, VALID_BUDGET, ENABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("job-discovery.enabled=true");
    }

    @Test
    void schedulerEnabled_rejectsBlankCron() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, VALID_EXECUTION, VALID_BUDGET,
                new JobDiscoveryProperties.Scheduler(true, "  ", "Europe/Warsaw", Duration.ofHours(2), Duration.ofMinutes(1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schedulerEnabled_rejectsInvalidCron() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, VALID_EXECUTION, VALID_BUDGET,
                new JobDiscoveryProperties.Scheduler(true, "not a cron", "Europe/Warsaw", Duration.ofHours(2), Duration.ofMinutes(1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schedulerEnabled_rejectsFiveFieldCron() {
        // Unix five-field cron (no seconds field) - Spring's CronExpression requires six fields.
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, VALID_EXECUTION, VALID_BUDGET,
                new JobDiscoveryProperties.Scheduler(true, "0 8 * * *", "Europe/Warsaw", Duration.ofHours(2), Duration.ofMinutes(1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schedulerEnabled_rejectsInvalidZone() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, VALID_EXECUTION, VALID_BUDGET,
                new JobDiscoveryProperties.Scheduler(true, "0 0 8 * * *", "Not/AZone", Duration.ofHours(2), Duration.ofMinutes(1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schedulerEnabled_rejectsBlankZone() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, VALID_EXECUTION, VALID_BUDGET,
                new JobDiscoveryProperties.Scheduler(true, "0 0 8 * * *", " ", Duration.ofHours(2), Duration.ofMinutes(1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schedulerEnabled_rejectsZeroLockAtMostFor() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, VALID_EXECUTION, VALID_BUDGET,
                new JobDiscoveryProperties.Scheduler(true, "0 0 8 * * *", "Europe/Warsaw", Duration.ZERO, Duration.ofMinutes(1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schedulerEnabled_rejectsNegativeLockAtMostFor() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, VALID_EXECUTION, VALID_BUDGET,
                new JobDiscoveryProperties.Scheduler(true, "0 0 8 * * *", "Europe/Warsaw", Duration.ofMinutes(-1), Duration.ZERO)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schedulerEnabled_rejectsLockAtMostForBelowOneMinute() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, VALID_EXECUTION, VALID_BUDGET,
                new JobDiscoveryProperties.Scheduler(true, "0 0 8 * * *", "Europe/Warsaw",
                        Duration.ofSeconds(59), Duration.ZERO)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schedulerEnabled_rejectsLockAtMostForAboveTwelveHours() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, VALID_EXECUTION, VALID_BUDGET,
                new JobDiscoveryProperties.Scheduler(true, "0 0 8 * * *", "Europe/Warsaw",
                        Duration.ofHours(12).plusSeconds(1), Duration.ofMinutes(1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schedulerEnabled_acceptsLockAtMostForBoundaries() {
        assertThatCode(() -> new JobDiscoveryProperties(true, VALID_EXECUTION, VALID_BUDGET,
                new JobDiscoveryProperties.Scheduler(true, "0 0 8 * * *", "Europe/Warsaw",
                        Duration.ofMinutes(1), Duration.ZERO)))
                .doesNotThrowAnyException();
        assertThatCode(() -> new JobDiscoveryProperties(true, VALID_EXECUTION, VALID_BUDGET,
                new JobDiscoveryProperties.Scheduler(true, "0 0 8 * * *", "Europe/Warsaw",
                        Duration.ofHours(12), Duration.ofMinutes(1))))
                .doesNotThrowAnyException();
    }

    @Test
    void schedulerEnabled_rejectsNegativeLockAtLeastFor() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, VALID_EXECUTION, VALID_BUDGET,
                new JobDiscoveryProperties.Scheduler(true, "0 0 8 * * *", "Europe/Warsaw",
                        Duration.ofHours(2), Duration.ofMinutes(-1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schedulerEnabled_acceptsZeroLockAtLeastFor() {
        assertThatCode(() -> new JobDiscoveryProperties(true, VALID_EXECUTION, VALID_BUDGET,
                new JobDiscoveryProperties.Scheduler(true, "0 0 8 * * *", "Europe/Warsaw",
                        Duration.ofHours(2), Duration.ZERO)))
                .doesNotThrowAnyException();
    }

    @Test
    void schedulerEnabled_rejectsLockAtLeastForEqualToLockAtMostFor() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, VALID_EXECUTION, VALID_BUDGET,
                new JobDiscoveryProperties.Scheduler(true, "0 0 8 * * *", "Europe/Warsaw",
                        Duration.ofMinutes(5), Duration.ofMinutes(5))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schedulerEnabled_rejectsLockAtLeastForAboveLockAtMostFor() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true, VALID_EXECUTION, VALID_BUDGET,
                new JobDiscoveryProperties.Scheduler(true, "0 0 8 * * *", "Europe/Warsaw",
                        Duration.ofMinutes(5), Duration.ofMinutes(6))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- execution: listing expansion (Sprint 12) -----------------------------------------------

    @Test
    void legacyFiveArgExecutionConstructor_defaultsListingExpansionFieldsToConservativeValues() {
        JobDiscoveryProperties.Execution execution = new JobDiscoveryProperties.Execution(3, 5, 5, 30, 50);

        assertThat(execution.maxListingPagesPerRun()).isEqualTo(2);
        assertThat(execution.maxCandidateLinksPerListing()).isEqualTo(25);
        assertThat(execution.minContentCharsForExtraction()).isZero();
    }

    @Test
    void enabled_rejectsNegativeMaxListingPagesPerRun() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 5, 5, 30, 50, -1, 25, 0), VALID_BUDGET, DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsMaxListingPagesPerRunAboveTwenty() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 5, 5, 30, 50, 21, 25, 0), VALID_BUDGET, DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_acceptsMaxListingPagesPerRunBoundaries() {
        assertThatCode(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 5, 5, 30, 50, 0, 25, 0), VALID_BUDGET, DISABLED_SCHEDULER))
                .doesNotThrowAnyException();
        assertThatCode(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 5, 5, 30, 50, 20, 25, 0), VALID_BUDGET, DISABLED_SCHEDULER))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_rejectsNegativeMaxCandidateLinksPerListing() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 5, 5, 30, 50, 2, -1, 0), VALID_BUDGET, DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsMaxCandidateLinksPerListingAboveTwoHundred() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 5, 5, 30, 50, 2, 201, 0), VALID_BUDGET, DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsNegativeMinContentCharsForExtraction() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 5, 5, 30, 50, 2, 25, -1), VALID_BUDGET, DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsMinContentCharsForExtractionAboveFiveThousand() {
        assertThatThrownBy(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 5, 5, 30, 50, 2, 25, 5001), VALID_BUDGET, DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_acceptsMinContentCharsForExtractionBoundaries() {
        assertThatCode(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 5, 5, 30, 50, 2, 25, 0), VALID_BUDGET, DISABLED_SCHEDULER))
                .doesNotThrowAnyException();
        assertThatCode(() -> new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 5, 5, 30, 50, 2, 25, 5000), VALID_BUDGET, DISABLED_SCHEDULER))
                .doesNotThrowAnyException();
    }
}
