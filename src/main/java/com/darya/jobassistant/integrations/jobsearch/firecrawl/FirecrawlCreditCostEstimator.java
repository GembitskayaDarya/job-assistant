package com.darya.jobassistant.integrations.jobsearch.firecrawl;

import com.darya.jobassistant.config.FirecrawlProperties;
import com.darya.jobassistant.integrations.jobsearch.JobSearchRequest;
import java.util.List;

/**
 * Turns a bounded list of {@link JobSearchRequest}s plus a per-run Scrape cap into an estimate of
 * the maximum Firecrawl credits that run is allowed to consume, using this project's own pricing
 * assumptions ({@link FirecrawlProperties.Cost}). Deliberately the only place this project's
 * Firecrawl pricing formula is expressed - {@code JobDiscoveryBudgetPolicy} only ever sees the
 * resulting numbers, never how they were computed, so a future pricing change never touches
 * provider-neutral budget policy or orchestration code.
 *
 * <p>Package-private: never crosses the {@code JobDiscoveryBudgetPort} boundary.
 *
 * <p>All arithmetic is overflow-safe ({@link Math#addExact(long, long)}/{@link
 * Math#multiplyExact(long, long)}); an overflow throws {@link ArithmeticException} rather than
 * silently wrapping into a negative number, which {@code FirecrawlJobDiscoveryBudgetAdapter} maps
 * to an {@code UNAVAILABLE} decision.
 */
final class FirecrawlCreditCostEstimator {

    private FirecrawlCreditCostEstimator() {
    }

    static Estimate estimate(List<JobSearchRequest> searchRequests, int maxScrapesPerRun, FirecrawlProperties.Cost cost) {
        long searchCredits = 0;
        for (JobSearchRequest request : searchRequests) {
            long creditBlocks = ceilDiv(request.maxResults(), cost.searchResultsPerCreditBlock());
            long requestCredits = Math.multiplyExact(creditBlocks, (long) cost.searchCreditsPerBlock());
            searchCredits = Math.addExact(searchCredits, requestCredits);
        }
        long scrapeCredits = Math.multiplyExact((long) maxScrapesPerRun, (long) cost.basicScrapeCredits());
        long totalCredits = Math.addExact(searchCredits, scrapeCredits);
        return new Estimate(searchCredits, scrapeCredits, totalCredits);
    }

    private static long ceilDiv(int numerator, int denominator) {
        return ((long) numerator + denominator - 1) / denominator;
    }

    record Estimate(long searchCredits, long scrapeCredits, long totalCredits) {
    }
}
