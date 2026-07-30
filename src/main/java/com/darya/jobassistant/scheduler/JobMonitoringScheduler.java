package com.darya.jobassistant.scheduler;

import com.darya.jobassistant.monitoring.JobMonitoringUseCase;
import com.darya.jobassistant.monitoring.config.JobMonitoringProperties;
import com.darya.jobassistant.monitoring.dto.JobMonitoringCommand;
import com.darya.jobassistant.vacancyrecommendation.config.RecommendationPolicyProperties;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Thin infrastructure trigger: builds a {@link JobMonitoringCommand} from configuration and
 * invokes {@link JobMonitoringUseCase#monitor}. Holds no workflow state between invocations -
 * every run is independent, and all business orchestration (ingestion, analysis, backlog
 * discovery, delivery) lives in the use case, never here.
 *
 * <p>Requiring {@link JobMonitoringUseCase} as a mandatory constructor dependency is deliberate:
 * its only current implementation is conditional on {@code telegram.enabled=true}, so if
 * {@code job-monitoring.enabled=true} while Telegram is disabled, the application fails to start
 * with a clear missing-bean error instead of this scheduler silently running ingestion and AI
 * analysis with no way to deliver notifications.
 *
 * <p>Multiple application instances may each poll and read the backlog on their own schedule;
 * this is intentionally not coordinated. Atomic vacancy insertion, the unique JobAnalysis per
 * vacancy, and atomic delivery reservation already prevent duplicate persistence and duplicate
 * sends - distributed scheduling coordination is a possible future efficiency improvement, not a
 * correctness requirement.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "job-monitoring", name = "enabled", havingValue = "true")
public class JobMonitoringScheduler {

    private final JobMonitoringUseCase jobMonitoringUseCase;
    private final JobMonitoringProperties jobMonitoringProperties;
    private final RecommendationPolicyProperties recommendationPolicyProperties;

    @Scheduled(
            fixedDelayString = "${job-monitoring.fixed-delay}",
            initialDelayString = "${job-monitoring.initial-delay}"
    )
    public void monitor() {
        log.debug("Starting scheduled job monitoring run");
        Instant startedAt = Instant.now();
        try {
            // JobMonitoringService already logs its own fetched/persisted/analyzed/matched/
            // notified/failed summary at info level - logging duration here avoids duplicating it.
            jobMonitoringUseCase.monitor(jobMonitoringProperties.toCommand(recommendationPolicyProperties.minimumScore()));
            log.debug("Scheduled job monitoring run completed in {}", Duration.between(startedAt, Instant.now()));
        } catch (RuntimeException e) {
            log.error("Scheduled job monitoring run failed - will resume on the next scheduled invocation", e);
        }
    }
}
