package com.darya.jobassistant.vacancyrecommendation.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Activation and processing bounds for the automatic recommendation pipeline ({@code
 * VacancyRecommendationProcessingService}/{@code VacancyRecommendationScheduler}). Matches {@code
 * JobDiscoveryProperties}'s validation convention: {@link #processing()} is validated whenever
 * {@link #enabled()} is {@code true}, and {@link #scheduler()}'s own fields are validated whenever
 * {@code scheduler.enabled=true} - independent of {@link #enabled}, so {@code
 * vacancy-recommendation.scheduler.enabled=true} together with {@code
 * vacancy-recommendation.enabled=false} fails startup with a clear configuration error rather than
 * the scheduler silently never running.
 *
 * <p>{@link #recipientChatId()} is deliberately this feature's own dedicated value, not borrowed
 * from {@code job-monitoring.recipient-chat-id}: unlike the match-score threshold (see {@code
 * RecommendationPolicyProperties}), "who receives automatic notifications" is not a single
 * project-wide business decision this project has committed to sharing, and reusing {@code
 * JobMonitoringProperties} here would make this feature's startup validation depend on {@code
 * job-monitoring.enabled} in a way that could silently defeat "missing required infrastructure
 * fails startup clearly".
 */
@ConfigurationProperties(prefix = "vacancy-recommendation")
public record VacancyRecommendationProperties(
        boolean enabled,
        Long recipientChatId,
        Processing processing,
        Scheduler scheduler
) {

    private static final int MIN_BATCH_SIZE = 1;
    private static final int MAX_BATCH_SIZE = 50;
    private static final int MIN_MAX_ATTEMPTS = 1;
    private static final int MAX_MAX_ATTEMPTS = 10;
    private static final Duration MIN_LEASE_DURATION = Duration.ofMinutes(1);
    private static final Duration MAX_LEASE_DURATION = Duration.ofHours(2);
    private static final Duration MAX_RETRY_MAX_DELAY = Duration.ofHours(24);
    private static final Duration MIN_SCHEDULER_FIXED_DELAY = Duration.ofMinutes(1);

    public VacancyRecommendationProperties {
        if (enabled) {
            if (recipientChatId == null || recipientChatId == 0L) {
                throw new IllegalArgumentException(
                        "vacancy-recommendation.recipient-chat-id must be a valid Telegram chat id "
                                + "when vacancy-recommendation.enabled=true");
            }
            if (processing == null) {
                throw new IllegalArgumentException(
                        "vacancy-recommendation.processing must be set when vacancy-recommendation.enabled=true");
            }
            requireInRange(processing.batchSize(), MIN_BATCH_SIZE, MAX_BATCH_SIZE,
                    "vacancy-recommendation.processing.batch-size");
            requireInRange(processing.maxAttempts(), MIN_MAX_ATTEMPTS, MAX_MAX_ATTEMPTS,
                    "vacancy-recommendation.processing.max-attempts");
            if (processing.leaseDuration() == null
                    || processing.leaseDuration().compareTo(MIN_LEASE_DURATION) < 0
                    || processing.leaseDuration().compareTo(MAX_LEASE_DURATION) > 0) {
                throw new IllegalArgumentException(
                        "vacancy-recommendation.processing.lease-duration must be between " + MIN_LEASE_DURATION
                                + " and " + MAX_LEASE_DURATION + " when vacancy-recommendation.enabled=true, but was "
                                + processing.leaseDuration());
            }
            if (processing.retryInitialDelay() == null || !processing.retryInitialDelay().isPositive()) {
                throw new IllegalArgumentException(
                        "vacancy-recommendation.processing.retry-initial-delay must be positive "
                                + "when vacancy-recommendation.enabled=true");
            }
            if (processing.retryMaxDelay() == null || processing.retryMaxDelay().compareTo(processing.retryInitialDelay()) < 0) {
                throw new IllegalArgumentException(
                        "vacancy-recommendation.processing.retry-max-delay ("
                                + processing.retryMaxDelay() + ") must be greater than or equal to "
                                + "vacancy-recommendation.processing.retry-initial-delay (" + processing.retryInitialDelay() + ")");
            }
            if (processing.retryMaxDelay().compareTo(MAX_RETRY_MAX_DELAY) > 0) {
                throw new IllegalArgumentException(
                        "vacancy-recommendation.processing.retry-max-delay must not exceed " + MAX_RETRY_MAX_DELAY
                                + ", but was " + processing.retryMaxDelay());
            }
        }
        if (scheduler != null && scheduler.enabled()) {
            if (!enabled) {
                throw new IllegalArgumentException(
                        "vacancy-recommendation.scheduler.enabled=true requires vacancy-recommendation.enabled=true - "
                                + "a scheduler cannot be requested for a recommendation pipeline that is itself disabled");
            }
            if (scheduler.fixedDelay() == null || scheduler.fixedDelay().compareTo(MIN_SCHEDULER_FIXED_DELAY) < 0) {
                throw new IllegalArgumentException(
                        "vacancy-recommendation.scheduler.fixed-delay must be at least " + MIN_SCHEDULER_FIXED_DELAY
                                + " when vacancy-recommendation.scheduler.enabled=true, but was " + scheduler.fixedDelay());
            }
            if (scheduler.initialDelay() == null || scheduler.initialDelay().isNegative()) {
                throw new IllegalArgumentException(
                        "vacancy-recommendation.scheduler.initial-delay must not be negative "
                                + "when vacancy-recommendation.scheduler.enabled=true");
            }
            if (scheduler.lockAtLeastFor() == null || scheduler.lockAtLeastFor().isNegative()) {
                throw new IllegalArgumentException(
                        "vacancy-recommendation.scheduler.lock-at-least-for must not be negative "
                                + "when vacancy-recommendation.scheduler.enabled=true");
            }
            // lockAtMostFor must comfortably exceed one full processing batch's worst case, which
            // is bounded by leaseDuration (the claim's own crash-recovery bound) - never shorter,
            // or ShedLock could release the cluster-wide lock while this node is still legitimately
            // working through its claimed batch, letting another node start overlapping work.
            if (scheduler.lockAtMostFor() == null || scheduler.lockAtMostFor().compareTo(processing.leaseDuration()) <= 0) {
                throw new IllegalArgumentException(
                        "vacancy-recommendation.scheduler.lock-at-most-for must be greater than "
                                + "vacancy-recommendation.processing.lease-duration (" + processing.leaseDuration()
                                + ") when vacancy-recommendation.scheduler.enabled=true, but was " + scheduler.lockAtMostFor());
            }
            if (scheduler.lockAtLeastFor().compareTo(scheduler.lockAtMostFor()) >= 0) {
                throw new IllegalArgumentException(
                        "vacancy-recommendation.scheduler.lock-at-least-for (" + scheduler.lockAtLeastFor()
                                + ") must be strictly less than vacancy-recommendation.scheduler.lock-at-most-for ("
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

    /** Per-run processing bounds and retry policy - see {@code VacancyRecommendationProcessingService}. */
    public record Processing(
            int batchSize,
            int maxAttempts,
            Duration leaseDuration,
            Duration retryInitialDelay,
            Duration retryMaxDelay
    ) {
    }

    /** Configuration for the optional {@code VacancyRecommendationScheduler} trigger. */
    public record Scheduler(
            boolean enabled,
            Duration fixedDelay,
            Duration initialDelay,
            Duration lockAtMostFor,
            Duration lockAtLeastFor
    ) {
    }
}
