package com.darya.jobassistant.monitoring.config;

import com.darya.jobassistant.monitoring.dto.JobMonitoringCommand;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the automatic {@code JobMonitoringScheduler} trigger. Validation only runs
 * when {@code enabled=true}, so the application can start with no configured recipient chat id
 * (or any other monitoring value) as long as automatic monitoring stays off - the constructor
 * throwing here fails the whole application context at startup, rather than the scheduler
 * failing repeatedly on every scheduled invocation.
 *
 * <p>Deliberately does not own the match-score threshold: that decision lives in the shared
 * {@code RecommendationPolicyProperties} instead (see that class's javadoc), so {@link
 * #toCommand(int)} takes the currently configured minimum score as a parameter rather than
 * reading a field of its own - {@code JobMonitoringScheduler} is the only caller, and it sources
 * that value from {@code RecommendationPolicyProperties}.
 */
@ConfigurationProperties(prefix = "job-monitoring")
public record JobMonitoringProperties(
        boolean enabled,
        Duration fixedDelay,
        Duration initialDelay,
        String keyword,
        int maxNotifications,
        Long recipientChatId
) {
    public JobMonitoringProperties {
        if (enabled) {
            if (fixedDelay == null || !fixedDelay.isPositive()) {
                throw new IllegalArgumentException(
                        "job-monitoring.fixed-delay must be positive when job-monitoring.enabled=true");
            }
            if (initialDelay == null || initialDelay.isNegative()) {
                throw new IllegalArgumentException(
                        "job-monitoring.initial-delay must not be negative when job-monitoring.enabled=true");
            }
            if (keyword == null || keyword.isBlank()) {
                throw new IllegalArgumentException(
                        "job-monitoring.keyword must not be blank when job-monitoring.enabled=true");
            }
            if (maxNotifications <= 0) {
                throw new IllegalArgumentException(
                        "job-monitoring.max-notifications must be positive when job-monitoring.enabled=true");
            }
            if (recipientChatId == null || recipientChatId == 0L) {
                throw new IllegalArgumentException(
                        "job-monitoring.recipient-chat-id must be a valid Telegram chat id when job-monitoring.enabled=true");
            }
        }
    }

    public JobMonitoringCommand toCommand(int minimumScore) {
        return new JobMonitoringCommand(keyword, minimumScore, maxNotifications, recipientChatId);
    }
}
