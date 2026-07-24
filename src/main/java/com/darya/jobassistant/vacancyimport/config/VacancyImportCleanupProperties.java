package com.darya.jobassistant.vacancyimport.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the scheduled {@code VacancyImportExpirationJob}. Validation only runs when
 * {@code enabled=true}, matching {@code JobMonitoringProperties}'s convention: a deployment (or a
 * minimal test/property-override context) that only sets {@code enabled=false} should not be
 * forced to also supply valid values for every other field just to disable cleanup - the
 * application.yml defaults cover the normal running app, but nothing else should have to assume
 * they're present.
 */
@ConfigurationProperties(prefix = "vacancy-import.cleanup")
public record VacancyImportCleanupProperties(boolean enabled, Duration fixedDelay, Duration initialDelay, int batchSize) {

    private static final int MAX_BATCH_SIZE = 1000;

    public VacancyImportCleanupProperties {
        if (enabled) {
            if (fixedDelay == null || !fixedDelay.isPositive()) {
                throw new IllegalArgumentException("vacancy-import.cleanup.fixed-delay must be positive when vacancy-import.cleanup.enabled=true");
            }
            if (initialDelay == null || initialDelay.isNegative()) {
                throw new IllegalArgumentException(
                        "vacancy-import.cleanup.initial-delay must not be negative when vacancy-import.cleanup.enabled=true");
            }
            if (batchSize <= 0 || batchSize > MAX_BATCH_SIZE) {
                throw new IllegalArgumentException(
                        "vacancy-import.cleanup.batch-size must be between 1 and " + MAX_BATCH_SIZE + ", but was " + batchSize);
            }
        }
    }
}
