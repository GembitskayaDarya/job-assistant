package com.darya.jobassistant.applicationmaterials.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sprint 10 Step 6: the single, centralized bound for {@code
 * ApplicationMaterialGeneration#isStaleInProgress} - how long an {@code IN_PROGRESS} generation
 * may run before {@code PrepareApplicationPackageUseCase} treats it as abandoned (the owning
 * process crashed or was killed) and recovers it. Mirrors {@code
 * ai.config.JobAnalysisProperties.claimStaleAfter}'s convention exactly - the equivalent bounded-
 * recovery timeout for the sibling {@code job_analysis} claim workflow - down to always being
 * eagerly bound and validated, defaulted in {@code application.yml} so no override is required.
 *
 * <p>{@link #staleInProgressTimeout} is deliberately more generous than {@code
 * job-analysis.claim-stale-after} (2 minutes): a tailored CV/cover-letter generation legitimately
 * does more work per attempt (a larger Career-History-aware prompt, then PDF rendering and file
 * storage) than a one-shot fit-analysis call, so it needs a wider safety margin before a still-
 * running, merely slow attempt is mistaken for an abandoned one.
 */
@ConfigurationProperties(prefix = "application-materials.generation")
public record ApplicationMaterialGenerationProperties(Duration staleInProgressTimeout) {

    /**
     * A normal generation (one AI call plus rendering) is expected to finish in well under a
     * minute; this floor keeps the timeout "comfortably longer than a normal request" even under a
     * misconfigured override, while still bounding recovery to a reasonable wait.
     */
    private static final Duration MIN_STALE_IN_PROGRESS_TIMEOUT = Duration.ofMinutes(5);

    /** Generous ceiling against a misconfigured value effectively disabling recovery. */
    private static final Duration MAX_STALE_IN_PROGRESS_TIMEOUT = Duration.ofHours(2);

    public ApplicationMaterialGenerationProperties {
        if (staleInProgressTimeout == null
                || staleInProgressTimeout.compareTo(MIN_STALE_IN_PROGRESS_TIMEOUT) < 0
                || staleInProgressTimeout.compareTo(MAX_STALE_IN_PROGRESS_TIMEOUT) > 0) {
            throw new IllegalArgumentException(
                    "application-materials.generation.stale-in-progress-timeout must be between "
                            + MIN_STALE_IN_PROGRESS_TIMEOUT + " and " + MAX_STALE_IN_PROGRESS_TIMEOUT
                            + ", but was " + staleInProgressTimeout);
        }
    }
}
