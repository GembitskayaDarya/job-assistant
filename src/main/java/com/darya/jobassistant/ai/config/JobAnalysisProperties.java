package com.darya.jobassistant.ai.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "job-analysis")
public record JobAnalysisProperties(Duration claimStaleAfter) {

    public JobAnalysisProperties {
        if (claimStaleAfter == null || !claimStaleAfter.isPositive()) {
            throw new IllegalArgumentException("job-analysis.claim-stale-after must be positive");
        }
    }
}
