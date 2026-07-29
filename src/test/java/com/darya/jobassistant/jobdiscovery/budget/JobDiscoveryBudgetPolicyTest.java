package com.darya.jobassistant.jobdiscovery.budget;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.jobdiscovery.config.JobDiscoveryProperties;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Unit-tests {@link JobDiscoveryBudgetPolicy}'s deterministic precedence in isolation, independent
 * of HTTP or cost estimation - see {@code FirecrawlCreditCostEstimatorTest} for the estimate side
 * and {@code FirecrawlJobDiscoveryBudgetAdapterTest} for the end-to-end wiring.
 */
class JobDiscoveryBudgetPolicyTest {

    private static final Instant START = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-07-31T23:59:59Z");
    private static final JobDiscoveryProperties.Budget BUDGET = new JobDiscoveryProperties.Budget(800, 200, 15);

    // --- Per-run limit -------------------------------------------------------------------------

    @Test
    void exactlyAtPerRunLimit_isAllowed() {
        JobDiscoveryCreditSnapshot snapshot = new JobDiscoveryCreditSnapshot(1000, 1000, START, END);

        JobDiscoveryBudgetDecision decision = JobDiscoveryBudgetPolicy.decide(10, 5, 15, BUDGET, snapshot);

        assertThat(decision.status()).isEqualTo(JobDiscoveryBudgetStatus.ALLOWED);
        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void abovePerRunLimit_isDenied() {
        JobDiscoveryCreditSnapshot snapshot = new JobDiscoveryCreditSnapshot(1000, 1000, START, END);

        JobDiscoveryBudgetDecision decision = JobDiscoveryBudgetPolicy.decide(11, 5, 16, BUDGET, snapshot);

        assertThat(decision.status()).isEqualTo(JobDiscoveryBudgetStatus.DENIED_PER_RUN_LIMIT);
        assertThat(decision.allowed()).isFalse();
    }

    // --- Monthly limit ---------------------------------------------------------------------------

    @Test
    void exactlyAtMonthlyLimit_isAllowed() {
        // used=785, estimate=15 -> projected monthly usage=800 == monthlyCreditLimit.
        JobDiscoveryCreditSnapshot snapshot = new JobDiscoveryCreditSnapshot(1000, 1785, START, END);

        JobDiscoveryBudgetDecision decision = JobDiscoveryBudgetPolicy.decide(10, 5, 15, BUDGET, snapshot);

        assertThat(decision.status()).isEqualTo(JobDiscoveryBudgetStatus.ALLOWED);
    }

    @Test
    void aboveMonthlyLimit_isDenied() {
        // used=786, estimate=15 -> projected monthly usage=801 > monthlyCreditLimit(800).
        JobDiscoveryCreditSnapshot snapshot = new JobDiscoveryCreditSnapshot(1000, 1786, START, END);

        JobDiscoveryBudgetDecision decision = JobDiscoveryBudgetPolicy.decide(10, 5, 15, BUDGET, snapshot);

        assertThat(decision.status()).isEqualTo(JobDiscoveryBudgetStatus.DENIED_MONTHLY_LIMIT);
    }

    // --- Reserve limit ---------------------------------------------------------------------------

    @Test
    void exactlyAtReserveBoundary_isAllowed() {
        // remaining=215, plan=220 -> used=5, monthly projection=20 (well under 800, so only the
        // reserve boundary is exercised); remainingAfterRun=215-15=200 == reserveCredits.
        JobDiscoveryCreditSnapshot snapshot = new JobDiscoveryCreditSnapshot(215, 220, START, END);

        JobDiscoveryBudgetDecision decision = JobDiscoveryBudgetPolicy.decide(10, 5, 15, BUDGET, snapshot);

        assertThat(decision.status()).isEqualTo(JobDiscoveryBudgetStatus.ALLOWED);
    }

    @Test
    void crossingReserve_isDenied() {
        // remaining=214, plan=220 -> used=6, monthly projection=21 (well under 800, so only the
        // reserve boundary is exercised); remainingAfterRun=214-15=199 < reserveCredits(200).
        JobDiscoveryCreditSnapshot snapshot = new JobDiscoveryCreditSnapshot(214, 220, START, END);

        JobDiscoveryBudgetDecision decision = JobDiscoveryBudgetPolicy.decide(10, 5, 15, BUDGET, snapshot);

        assertThat(decision.status()).isEqualTo(JobDiscoveryBudgetStatus.DENIED_RESERVE_LIMIT);
    }

    // --- Precedence ------------------------------------------------------------------------------

    @Test
    void precedence_perRunLimitWinsOverMonthlyAndReserve() {
        // Violates per-run (16 > 15), monthly (used=1000 -> projected way above 800), and reserve
        // (remaining=10 -> remainingAfterRun negative) all at once.
        JobDiscoveryCreditSnapshot snapshot = new JobDiscoveryCreditSnapshot(10, 2000, START, END);

        JobDiscoveryBudgetDecision decision = JobDiscoveryBudgetPolicy.decide(11, 5, 16, BUDGET, snapshot);

        assertThat(decision.status()).isEqualTo(JobDiscoveryBudgetStatus.DENIED_PER_RUN_LIMIT);
    }

    @Test
    void precedence_monthlyLimitWinsOverReserve_whenPerRunLimitNotViolated() {
        // Per-run limit satisfied (15 <= 15). Violates both monthly (used=1780 + 15 = 1795 > 800)
        // and reserve (remaining=10 -> remainingAfterRun=-5 < 200).
        JobDiscoveryCreditSnapshot snapshot = new JobDiscoveryCreditSnapshot(10, 1790, START, END);

        JobDiscoveryBudgetDecision decision = JobDiscoveryBudgetPolicy.decide(10, 5, 15, BUDGET, snapshot);

        assertThat(decision.status()).isEqualTo(JobDiscoveryBudgetStatus.DENIED_MONTHLY_LIMIT);
    }

    // --- Snapshot interaction ----------------------------------------------------------------

    @Test
    void remainingCreditsGreaterThanPlanCredits_yieldsZeroUsedCredits() {
        JobDiscoveryCreditSnapshot snapshot = new JobDiscoveryCreditSnapshot(1500, 1000, START, END);

        JobDiscoveryBudgetDecision decision = JobDiscoveryBudgetPolicy.decide(10, 5, 15, BUDGET, snapshot);

        assertThat(decision.estimatedUsedCredits()).isZero();
        assertThat(decision.status()).isEqualTo(JobDiscoveryBudgetStatus.ALLOWED);
    }

    @Test
    void decision_carriesEstimateAndConfiguredLimitsVerbatim() {
        JobDiscoveryCreditSnapshot snapshot = new JobDiscoveryCreditSnapshot(1000, 1000, START, END);

        JobDiscoveryBudgetDecision decision = JobDiscoveryBudgetPolicy.decide(10, 5, 15, BUDGET, snapshot);

        assertThat(decision.estimatedSearchCredits()).isEqualTo(10);
        assertThat(decision.estimatedScrapeCredits()).isEqualTo(5);
        assertThat(decision.estimatedTotalCredits()).isEqualTo(15);
        assertThat(decision.configuredMonthlyLimit()).isEqualTo(800);
        assertThat(decision.configuredReserveCredits()).isEqualTo(200);
        assertThat(decision.configuredPerRunLimit()).isEqualTo(15);
        assertThat(decision.remainingCredits()).isEqualTo(1000);
        assertThat(decision.planCredits()).isEqualTo(1000);
        assertThat(decision.billingPeriodStart()).isEqualTo(START);
        assertThat(decision.billingPeriodEnd()).isEqualTo(END);
    }
}
