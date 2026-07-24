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
 * <p>Keyword/score/limit/recipient validation is intentionally not duplicated here: it delegates
 * to {@link JobMonitoringCommand}'s own compact constructor via {@link #toCommand()}, which is
 * exercised eagerly below so a bad value fails at startup instead of on the first scheduled run.
 */
@ConfigurationProperties(prefix = "job-monitoring")
public record JobMonitoringProperties(
        boolean enabled,
        Duration fixedDelay,
        Duration initialDelay,
        String keyword,
        int minimumScore,
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
            new JobMonitoringCommand(keyword, minimumScore, maxNotifications, recipientChatId);
        }
    }

    public JobMonitoringCommand toCommand() {
        return new JobMonitoringCommand(keyword, minimumScore, maxNotifications, recipientChatId);
    }
}
