package com.darya.jobassistant.integrations.jobsearch.firecrawl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.config.FirecrawlProperties;
import com.darya.jobassistant.integrations.jobsearch.JobSearchRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class FirecrawlCreditCostEstimatorTest {

    private static final FirecrawlProperties.Cost DEFAULT_COST = new FirecrawlProperties.Cost(10, 2, 1);

    @Test
    void estimate_defaultThreeTenResultSearchesPlusFiveScrapes_totalsElevenCredits() {
        List<JobSearchRequest> requests = List.of(
                new JobSearchRequest("q1", 10), new JobSearchRequest("q2", 10), new JobSearchRequest("q3", 10));

        FirecrawlCreditCostEstimator.Estimate estimate = FirecrawlCreditCostEstimator.estimate(requests, 5, DEFAULT_COST);

        assertThat(estimate.searchCredits()).isEqualTo(6);
        assertThat(estimate.scrapeCredits()).isEqualTo(5);
        assertThat(estimate.totalCredits()).isEqualTo(11);
    }

    @Test
    void estimate_oneTenResultSearch_estimatesTwoCredits() {
        FirecrawlCreditCostEstimator.Estimate estimate =
                FirecrawlCreditCostEstimator.estimate(List.of(new JobSearchRequest("q1", 10)), 0, DEFAULT_COST);

        assertThat(estimate.searchCredits()).isEqualTo(2);
    }

    @Test
    void estimate_oneElevenResultSearch_roundsUpToFourCredits() {
        FirecrawlCreditCostEstimator.Estimate estimate =
                FirecrawlCreditCostEstimator.estimate(List.of(new JobSearchRequest("q1", 11)), 0, DEFAULT_COST);

        assertThat(estimate.searchCredits()).isEqualTo(4);
    }

    @Test
    void estimate_scrapeCredits_usesMaxScrapesPerRunOnly() {
        FirecrawlCreditCostEstimator.Estimate estimate =
                FirecrawlCreditCostEstimator.estimate(List.of(new JobSearchRequest("q1", 10)), 7, DEFAULT_COST);

        assertThat(estimate.scrapeCredits()).isEqualTo(7);
    }

    @Test
    void estimate_onlyIncludesRequestsPassedIn_callerAlreadyBounded() {
        List<JobSearchRequest> boundedToTwo = List.of(new JobSearchRequest("q1", 10), new JobSearchRequest("q2", 10));

        FirecrawlCreditCostEstimator.Estimate estimate = FirecrawlCreditCostEstimator.estimate(boundedToTwo, 0, DEFAULT_COST);

        assertThat(estimate.searchCredits()).isEqualTo(4);
    }

    @Test
    void estimate_changingCostProperties_changesEstimateDeterministically() {
        FirecrawlProperties.Cost doubledCost = new FirecrawlProperties.Cost(10, 4, 2);
        List<JobSearchRequest> requests = List.of(new JobSearchRequest("q1", 10));

        FirecrawlCreditCostEstimator.Estimate estimate = FirecrawlCreditCostEstimator.estimate(requests, 5, doubledCost);

        assertThat(estimate.searchCredits()).isEqualTo(4);
        assertThat(estimate.scrapeCredits()).isEqualTo(10);
        assertThat(estimate.totalCredits()).isEqualTo(14);
    }

    @Test
    void estimate_integerOverflow_throwsArithmeticException() {
        // Each request alone contributes ~4.6e18 credits (Integer.MAX_VALUE blocks *
        // Integer.MAX_VALUE credits-per-block, still within a long); summing three of them via
        // Math.addExact crosses Long.MAX_VALUE (~9.2e18) on the third accumulation.
        List<JobSearchRequest> requests = List.of(
                new JobSearchRequest("q1", Integer.MAX_VALUE),
                new JobSearchRequest("q2", Integer.MAX_VALUE),
                new JobSearchRequest("q3", Integer.MAX_VALUE));
        FirecrawlProperties.Cost extremeCost = new FirecrawlProperties.Cost(1, Integer.MAX_VALUE, 1);

        assertThatThrownBy(() -> FirecrawlCreditCostEstimator.estimate(requests, 1, extremeCost))
                .isInstanceOf(ArithmeticException.class);
    }
}
