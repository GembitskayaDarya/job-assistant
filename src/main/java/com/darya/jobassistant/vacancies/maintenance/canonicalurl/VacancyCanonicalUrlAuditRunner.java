package com.darya.jobassistant.vacancies.maintenance.canonicalurl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Runs {@link VacancyCanonicalUrlAuditService#audit()} exactly once, after the application
 * context is fully up ({@link ApplicationReadyEvent}, not an {@code ApplicationRunner}/{@code
 * CommandLineRunner} - those run as part of {@code SpringApplication.run()} itself and would delay
 * "started" being reported; this listener fires only once startup has already completed, so a slow
 * or failing audit can never be mistaken for - or block - normal application startup).
 *
 * <p>This bean exists at all only when {@code vacancy-canonical-url-audit.enabled=true}, which is
 * not the default. When disabled, nothing here runs, nothing here is even constructed, and
 * application behavior is entirely unchanged.
 *
 * <p>No scheduler, no recurring trigger, no JVM exit: this is a one-shot maintenance read, not a
 * background job. A failure inside {@link VacancyCanonicalUrlAuditService#audit()} is caught and
 * logged, never rethrown - an audit problem must never look like (or cause) an application
 * startup failure.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "vacancy-canonical-url-audit", name = "enabled", havingValue = "true")
public class VacancyCanonicalUrlAuditRunner {

    private final VacancyCanonicalUrlAuditService auditService;

    @EventListener(ApplicationReadyEvent.class)
    public void runAudit() {
        try {
            VacancyCanonicalUrlAuditReport report = auditService.audit();
            logReport(report);
        } catch (RuntimeException e) {
            log.error("Vacancy canonical URL audit failed - AUDIT ONLY, no canonical_url values were modified", e);
        }
    }

    private void logReport(VacancyCanonicalUrlAuditReport report) {
        log.info("AUDIT ONLY - no canonical_url values were modified");
        log.info("Vacancy canonical URL audit summary: totalLegacyRows={}, safeToBackfillRows={}, "
                        + "invalidSourceUrlRows={}, legacyToLegacyCollisionGroups={}, legacyToLegacyCollisionRows={}, "
                        + "legacyToCurrentCollisionRows={}, scannedBatchCount={}, issuesReported={}, issuesOmitted={}, truncated={}",
                report.totalLegacyRows(), report.safeToBackfillRows(), report.invalidSourceUrlRows(),
                report.legacyToLegacyCollisionGroups(), report.legacyToLegacyCollisionRows(),
                report.legacyToCurrentCollisionRows(), report.scannedBatchCount(),
                report.issues().size(), report.omittedIssueCount(), report.issuesTruncated());

        // Deliberately omits VacancyCanonicalUrlAuditIssue#canonicalCandidate - it may carry
        // non-tracking query parameters from the original source URL, so only UUIDs (never a URL,
        // token, or other free-text value) are written to the log.
        for (VacancyCanonicalUrlAuditIssue issue : report.issues()) {
            log.info("Vacancy canonical URL audit issue: type={}, vacancyId={}, relatedVacancyIds={}",
                    issue.issueType(), issue.vacancyId(), issue.relatedVacancyIds());
        }
    }
}
