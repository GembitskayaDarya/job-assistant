package com.darya.jobassistant.jobdiscovery.config;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.util.StringUtils;

/**
 * Activation and per-run cost bounds for {@code JobDiscoveryService}. Deliberately provider-
 * neutral: nothing here names Firecrawl or OpenAI - {@code JobDiscoveryService} itself is only
 * wired up when {@code enabled=true} <em>and</em> a real {@code JobSearchPort}/{@code
 * JobPageFetchPort} bean exists (today, only when {@code firecrawl.enabled=true}); if discovery is
 * enabled without those beans present, Spring fails application startup with its own clear
 * "no qualifying bean" message rather than this class silently letting a non-functional service be
 * created.
 *
 * <p>Validation only runs when {@code enabled=true}, matching {@code FirecrawlProperties}/{@code
 * JobMonitoringProperties}'s convention: the application must keep starting with defaulted (even
 * out-of-range, if ever overridden) execution bounds as long as discovery stays off.
 *
 * <p>{@link #budget()} is deliberately provider-neutral: it names no Firecrawl concept and holds
 * only spend-policy limits (monthly cap, reserve, per-run cap) - see {@code
 * FirecrawlProperties.Cost} for the separate, provider-specific pricing assumptions used to turn a
 * planned run into a credit estimate, and {@code JobDiscoveryBudgetPolicy} for how these limits are
 * applied to that estimate.
 *
 * <p>{@link #scheduler()} configures the optional daily {@code JobDiscoveryScheduler} trigger.
 * Unlike {@link #execution()}/{@link #budget()}, {@link Scheduler}'s own fields are validated
 * whenever {@code scheduler.enabled=true} - independent of {@link #enabled}, so that {@code
 * job-discovery.scheduler.enabled=true} together with {@code job-discovery.enabled=false} fails
 * startup with a clear configuration error rather than the scheduler silently never running (a
 * {@code JobDiscoveryScheduler} bean cannot exist at all in that combination, since it requires a
 * real {@code JobDiscoveryService} bean, itself gated on {@code job-discovery.enabled=true} - this
 * check surfaces that same misconfiguration earlier and more clearly, directly from the property
 * binder, instead of only as a missing-bean startup failure).
 */
@ConfigurationProperties(prefix = "job-discovery")
public record JobDiscoveryProperties(boolean enabled, Execution execution, Budget budget, Scheduler scheduler) {

    private static final int MIN_MAX_QUERIES_PER_RUN = 1;
    private static final int MAX_MAX_QUERIES_PER_RUN = 10;
    private static final int MIN_MAX_SCRAPES_PER_RUN = 1;
    private static final int MAX_MAX_SCRAPES_PER_RUN = 50;
    private static final int MIN_MAX_EXTRACTIONS_PER_RUN = 1;
    private static final int MAX_MAX_EXTRACTIONS_PER_RUN = 50;
    private static final int MIN_MAX_UNIQUE_REFERENCES_PER_RUN = 1;
    private static final int MAX_MAX_UNIQUE_REFERENCES_PER_RUN = 500;
    private static final int MIN_MAX_REPORTED_ISSUES = 0;
    private static final int MAX_MAX_REPORTED_ISSUES = 1000;
    private static final int MIN_MAX_LISTING_PAGES_PER_RUN = 0;
    private static final int MAX_MAX_LISTING_PAGES_PER_RUN = 20;
    private static final int MIN_MAX_CANDIDATE_LINKS_PER_LISTING = 0;
    private static final int MAX_MAX_CANDIDATE_LINKS_PER_LISTING = 200;
    private static final int MIN_MIN_CONTENT_CHARS_FOR_EXTRACTION = 0;
    private static final int MAX_MIN_CONTENT_CHARS_FOR_EXTRACTION = 5000;
    private static final long MIN_MONTHLY_CREDIT_LIMIT = 1;
    private static final long MAX_MONTHLY_CREDIT_LIMIT = 1_000_000;
    private static final long MIN_RESERVE_CREDITS = 0;
    private static final long MAX_RESERVE_CREDITS = 1_000_000;
    private static final long MIN_MAX_ESTIMATED_CREDITS_PER_RUN = 1;
    private static final long MAX_MAX_ESTIMATED_CREDITS_PER_RUN = 100_000;
    private static final Duration MIN_LOCK_AT_MOST_FOR = Duration.ofMinutes(1);
    private static final Duration MAX_LOCK_AT_MOST_FOR = Duration.ofHours(12);

    public JobDiscoveryProperties {
        if (enabled) {
            if (execution == null) {
                throw new IllegalArgumentException("job-discovery.execution must be set when job-discovery.enabled=true");
            }
            requireInRange(execution.maxQueriesPerRun(), MIN_MAX_QUERIES_PER_RUN, MAX_MAX_QUERIES_PER_RUN,
                    "job-discovery.execution.max-queries-per-run");
            requireInRange(execution.maxScrapesPerRun(), MIN_MAX_SCRAPES_PER_RUN, MAX_MAX_SCRAPES_PER_RUN,
                    "job-discovery.execution.max-scrapes-per-run");
            requireInRange(execution.maxExtractionsPerRun(), MIN_MAX_EXTRACTIONS_PER_RUN, MAX_MAX_EXTRACTIONS_PER_RUN,
                    "job-discovery.execution.max-extractions-per-run");
            requireInRange(execution.maxUniqueReferencesPerRun(), MIN_MAX_UNIQUE_REFERENCES_PER_RUN, MAX_MAX_UNIQUE_REFERENCES_PER_RUN,
                    "job-discovery.execution.max-unique-references-per-run");
            requireInRange(execution.maxReportedIssues(), MIN_MAX_REPORTED_ISSUES, MAX_MAX_REPORTED_ISSUES,
                    "job-discovery.execution.max-reported-issues");
            requireInRange(execution.maxListingPagesPerRun(), MIN_MAX_LISTING_PAGES_PER_RUN, MAX_MAX_LISTING_PAGES_PER_RUN,
                    "job-discovery.execution.max-listing-pages-per-run");
            requireInRange(execution.maxCandidateLinksPerListing(), MIN_MAX_CANDIDATE_LINKS_PER_LISTING, MAX_MAX_CANDIDATE_LINKS_PER_LISTING,
                    "job-discovery.execution.max-candidate-links-per-listing");
            requireInRange(execution.minContentCharsForExtraction(), MIN_MIN_CONTENT_CHARS_FOR_EXTRACTION, MAX_MIN_CONTENT_CHARS_FOR_EXTRACTION,
                    "job-discovery.execution.min-content-chars-for-extraction");
            if (execution.maxExtractionsPerRun() > execution.maxScrapesPerRun()) {
                throw new IllegalArgumentException(
                        "job-discovery.execution.max-extractions-per-run (" + execution.maxExtractionsPerRun()
                                + ") must not exceed job-discovery.execution.max-scrapes-per-run (" + execution.maxScrapesPerRun() + ")");
            }
            if (budget == null) {
                throw new IllegalArgumentException("job-discovery.budget must be set when job-discovery.enabled=true");
            }
            requireInRange(budget.monthlyCreditLimit(), MIN_MONTHLY_CREDIT_LIMIT, MAX_MONTHLY_CREDIT_LIMIT,
                    "job-discovery.budget.monthly-credit-limit");
            requireInRange(budget.reserveCredits(), MIN_RESERVE_CREDITS, MAX_RESERVE_CREDITS,
                    "job-discovery.budget.reserve-credits");
            requireInRange(budget.maxEstimatedCreditsPerRun(), MIN_MAX_ESTIMATED_CREDITS_PER_RUN, MAX_MAX_ESTIMATED_CREDITS_PER_RUN,
                    "job-discovery.budget.max-estimated-credits-per-run");
            if (budget.maxEstimatedCreditsPerRun() > budget.monthlyCreditLimit()) {
                throw new IllegalArgumentException(
                        "job-discovery.budget.max-estimated-credits-per-run (" + budget.maxEstimatedCreditsPerRun()
                                + ") must not exceed job-discovery.budget.monthly-credit-limit (" + budget.monthlyCreditLimit() + ")");
            }
        }
        if (scheduler != null && scheduler.enabled()) {
            if (!enabled) {
                throw new IllegalArgumentException(
                        "job-discovery.scheduler.enabled=true requires job-discovery.enabled=true - "
                                + "a scheduler cannot be requested for a discovery service that is itself disabled");
            }
            if (!StringUtils.hasText(scheduler.cron())) {
                throw new IllegalArgumentException(
                        "job-discovery.scheduler.cron must not be blank when job-discovery.scheduler.enabled=true");
            }
            try {
                CronExpression.parse(scheduler.cron());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "job-discovery.scheduler.cron is not a valid Spring six-field cron expression: "
                                + scheduler.cron(), e);
            }
            if (!StringUtils.hasText(scheduler.zone())) {
                throw new IllegalArgumentException(
                        "job-discovery.scheduler.zone must not be blank when job-discovery.scheduler.enabled=true");
            }
            try {
                ZoneId.of(scheduler.zone());
            } catch (DateTimeException e) {
                throw new IllegalArgumentException(
                        "job-discovery.scheduler.zone is not a valid zone id: " + scheduler.zone(), e);
            }
            if (scheduler.lockAtMostFor() == null
                    || scheduler.lockAtMostFor().compareTo(MIN_LOCK_AT_MOST_FOR) < 0
                    || scheduler.lockAtMostFor().compareTo(MAX_LOCK_AT_MOST_FOR) > 0) {
                throw new IllegalArgumentException(
                        "job-discovery.scheduler.lock-at-most-for must be between " + MIN_LOCK_AT_MOST_FOR + " and "
                                + MAX_LOCK_AT_MOST_FOR + " when job-discovery.scheduler.enabled=true, but was "
                                + scheduler.lockAtMostFor());
            }
            if (scheduler.lockAtLeastFor() == null || scheduler.lockAtLeastFor().isNegative()) {
                throw new IllegalArgumentException(
                        "job-discovery.scheduler.lock-at-least-for must not be negative when "
                                + "job-discovery.scheduler.enabled=true, but was " + scheduler.lockAtLeastFor());
            }
            if (scheduler.lockAtLeastFor().compareTo(scheduler.lockAtMostFor()) >= 0) {
                throw new IllegalArgumentException(
                        "job-discovery.scheduler.lock-at-least-for (" + scheduler.lockAtLeastFor()
                                + ") must be strictly less than job-discovery.scheduler.lock-at-most-for ("
                                + scheduler.lockAtMostFor() + ")");
            }
        }
    }

    private static void requireInRange(int value, int min, int max, String propertyName) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    propertyName + " must be between " + min + " and " + max + ", but was " + value);
        }
    }

    private static void requireInRange(long value, long min, long max, String propertyName) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    propertyName + " must be between " + min + " and " + max + ", but was " + value);
        }
    }

    /**
     * Per-run hard cost bounds - see {@code JobDiscoveryService} for how each one is enforced.
     *
     * <p>{@link #maxListingPagesPerRun}, {@link #maxCandidateLinksPerListing}, and {@link
     * #minContentCharsForExtraction} were added in Sprint 12 for listing-page expansion; a listing
     * page's own fetch still consumes the same {@link #maxScrapesPerRun} budget as any direct
     * candidate (there is exactly one Firecrawl-scrape budget, not two), so {@link
     * #maxListingPagesPerRun} exists only to stop listing pages from crowding out that shared
     * budget, not to grant them a separate one.
     */
    public record Execution(
            int maxQueriesPerRun,
            int maxScrapesPerRun,
            int maxExtractionsPerRun,
            int maxUniqueReferencesPerRun,
            int maxReportedIssues,
            int maxListingPagesPerRun,
            int maxCandidateLinksPerListing,
            int minContentCharsForExtraction
    ) {
        private static final int DEFAULT_MAX_LISTING_PAGES_PER_RUN = 2;
        private static final int DEFAULT_MAX_CANDIDATE_LINKS_PER_LISTING = 25;
        /** {@code 0} - the check is opt-in (see {@code JobDiscoveryService}), never on by surprise. */
        private static final int DEFAULT_MIN_CONTENT_CHARS_FOR_EXTRACTION = 0;

        /**
         * Explicit canonical constructor (rather than the implicit one a record normally gets),
         * annotated {@code @ConstructorBinding}, because this record declares a second,
         * pre-Sprint-12-shaped constructor below: Spring Boot's relaxed configuration-properties
         * binder requires an explicit choice whenever a bindable type has more than one
         * constructor, record or not.
         */
        @ConstructorBinding
        public Execution(
                int maxQueriesPerRun, int maxScrapesPerRun, int maxExtractionsPerRun,
                int maxUniqueReferencesPerRun, int maxReportedIssues,
                @DefaultValue("2") int maxListingPagesPerRun,
                @DefaultValue("25") int maxCandidateLinksPerListing,
                @DefaultValue("0") int minContentCharsForExtraction) {
            this.maxQueriesPerRun = maxQueriesPerRun;
            this.maxScrapesPerRun = maxScrapesPerRun;
            this.maxExtractionsPerRun = maxExtractionsPerRun;
            this.maxUniqueReferencesPerRun = maxUniqueReferencesPerRun;
            this.maxReportedIssues = maxReportedIssues;
            this.maxListingPagesPerRun = maxListingPagesPerRun;
            this.maxCandidateLinksPerListing = maxCandidateLinksPerListing;
            this.minContentCharsForExtraction = minContentCharsForExtraction;
        }

        /**
         * Pre-Sprint-12 shape, kept so every existing caller that predates listing expansion keeps
         * compiling unchanged, defaulted to conservative, effectively-off Sprint 12 defaults (a
         * real deployment configures these explicitly - see {@code application.yml}).
         */
        public Execution(int maxQueriesPerRun, int maxScrapesPerRun, int maxExtractionsPerRun,
                          int maxUniqueReferencesPerRun, int maxReportedIssues) {
            this(maxQueriesPerRun, maxScrapesPerRun, maxExtractionsPerRun, maxUniqueReferencesPerRun,
                    maxReportedIssues, DEFAULT_MAX_LISTING_PAGES_PER_RUN, DEFAULT_MAX_CANDIDATE_LINKS_PER_LISTING,
                    DEFAULT_MIN_CONTENT_CHARS_FOR_EXTRACTION);
        }
    }

    /**
     * Provider-neutral spend-policy limits enforced by {@code JobDiscoveryBudgetPolicy} against a
     * verified Firecrawl credit snapshot before every non-empty discovery run.
     */
    public record Budget(
            long monthlyCreditLimit,
            long reserveCredits,
            long maxEstimatedCreditsPerRun
    ) {
    }

    /**
     * Configuration for the optional daily {@code JobDiscoveryScheduler} trigger - see the class
     * javadoc for why these fields are validated independently of {@link #enabled}.
     *
     * <p>{@link #lockAtMostFor()} must stay comfortably larger than the maximum expected discovery
     * duration (so a still-running node is never overtaken by a second acquisition while genuinely
     * healthy) and shorter than the intended scheduling interval (so a crashed node's stale lock
     * cannot suppress the next legitimate occurrence indefinitely) - this class cannot verify
     * either relationship automatically, since it does not attempt to infer the interval between
     * arbitrary cron expressions; both must be sized deliberately by whoever configures them.
     */
    public record Scheduler(
            boolean enabled,
            String cron,
            String zone,
            Duration lockAtMostFor,
            Duration lockAtLeastFor
    ) {
    }
}
