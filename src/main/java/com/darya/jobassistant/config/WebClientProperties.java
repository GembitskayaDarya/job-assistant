package com.darya.jobassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "webclient")
public record WebClientProperties(
        long timeoutMs,
        int maxConnections,
        long maxIdleTimeMs,
        long maxLifeTimeMs,
        long pendingAcquireTimeoutMs,
        long evictInBackgroundMs,
        int maxInMemorySizeBytes
) {
}