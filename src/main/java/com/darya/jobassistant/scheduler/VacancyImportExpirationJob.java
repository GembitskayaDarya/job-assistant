package com.darya.jobassistant.scheduler;

import com.darya.jobassistant.vacancyimport.ExpireVacancyImportSessionsUseCase;
import com.darya.jobassistant.vacancyimport.dto.ExpireVacancyImportSessionsResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Thin infrastructure trigger: invokes {@link ExpireVacancyImportSessionsUseCase#expireBatch()}
 * on a schedule and logs the result. Holds no repository dependency and no domain state mutation
 * of its own - all of that lives in the use case.
 *
 * <p>Unlike {@code JobMonitoringScheduler}/{@code VacancyIngestionJob} (both off by default -
 * they make external network/AI calls with real cost), this defaults to <em>enabled</em>
 * ({@code matchIfMissing = true}): cleanup only touches this application's own local database, so
 * it is safe to run out of the box and keeps the guided-import workflow self-cleaning without
 * requiring explicit opt-in.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "vacancy-import.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class VacancyImportExpirationJob {

    private final ExpireVacancyImportSessionsUseCase expireVacancyImportSessionsUseCase;

    @Scheduled(
            fixedDelayString = "${vacancy-import.cleanup.fixed-delay}",
            initialDelayString = "${vacancy-import.cleanup.initial-delay}"
    )
    public void expire() {
        try {
            ExpireVacancyImportSessionsResult result = expireVacancyImportSessionsUseCase.expireBatch();
            log.info("Vacancy import cleanup completed: candidates={}, expired={}, skipped={}, failed={}",
                    result.candidateCount(), result.expiredCount(), result.skippedCount(), result.failedCount());
        } catch (RuntimeException e) {
            log.error("Scheduled vacancy import cleanup failed - will resume on the next scheduled invocation", e);
        }
    }
}
