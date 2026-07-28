package com.darya.jobassistant.vacancies.maintenance.canonicalurl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Runs {@link VacancyCanonicalUrlBackfillService#dryRun()} or {@link
 * VacancyCanonicalUrlBackfillService#apply()} - whichever {@code vacancy-canonical-url-backfill.mode}
 * selects - exactly once, after the application context is fully up ({@link
 * ApplicationReadyEvent}, for the same reason {@code VacancyCanonicalUrlAuditRunner} uses it rather
 * than an {@code ApplicationRunner}: this must never delay or be mistaken for normal startup).
 *
 * <p>This bean exists at all only when {@code vacancy-canonical-url-backfill.enabled=true}, which
 * is not the default; {@code mode} defaults to {@code DRY_RUN} even when enabled. When disabled,
 * nothing here runs or is even constructed - application behavior is entirely unchanged.
 *
 * <h2>DRY_RUN failures are logged and swallowed; APPLY failures are not</h2>
 *
 * A failed DRY_RUN is a read-only classification problem (e.g. a transient database issue) - it is
 * logged at {@code error} and otherwise ignored, matching {@code VacancyCanonicalUrlAuditRunner}'s
 * convention, since a read that failed changed nothing and must never look like an application
 * failure.
 *
 * <p>APPLY is different: it is an explicitly requested maintenance write, and the operator running
 * it needs to know immediately, unambiguously, whether it actually committed. A blocked refusal
 * ({@link VacancyCanonicalUrlBackfillBlockedException}) or an invariant violation ({@link
 * VacancyCanonicalUrlBackfillInvariantViolationException}) - or any other unexpected failure - is
 * logged at {@code error} <em>and rethrown</em>, deliberately never swallowed: a rolled-back APPLY
 * must never be reported as, or mistaken for, a quiet success.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "vacancy-canonical-url-backfill", name = "enabled", havingValue = "true")
public class VacancyCanonicalUrlBackfillRunner {

    private final VacancyCanonicalUrlBackfillService backfillService;
    private final VacancyCanonicalUrlBackfillProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void runBackfill() {
        switch (properties.mode()) {
            case DRY_RUN -> runDryRun();
            case APPLY -> runApply();
        }
    }

    private void runDryRun() {
        try {
            VacancyCanonicalUrlBackfillResult result = backfillService.dryRun();
            log.info("DRY RUN ONLY - no canonical_url values were modified");
            logSummary(result);
        } catch (RuntimeException e) {
            log.error("Vacancy canonical URL backfill dry run failed - no canonical_url values were modified", e);
        }
    }

    private void runApply() {
        VacancyCanonicalUrlBackfillResult result;
        try {
            result = backfillService.apply();
        } catch (VacancyCanonicalUrlBackfillBlockedException e) {
            log.error("Vacancy canonical URL backfill APPLY refused - blockers remain: invalidSourceUrlRows={}, "
                            + "legacyToLegacyCollisionRows={}, legacyToCurrentCollisionRows={}, sampleBlockingVacancyIds={} "
                            + "- no canonical_url values were modified",
                    e.invalidSourceUrlCount(), e.legacyToLegacyCollisionRowCount(), e.legacyToCurrentCollisionRowCount(),
                    e.sampleBlockingVacancyIds());
            throw e;
        } catch (RuntimeException e) {
            log.error("Vacancy canonical URL backfill APPLY failed and rolled back - no canonical_url values were modified", e);
            throw e;
        }
        log.info("Vacancy canonical URL backfill APPLY committed successfully");
        logSummary(result);
    }

    private void logSummary(VacancyCanonicalUrlBackfillResult result) {
        log.info("Vacancy canonical URL backfill summary: mode={}, legacyRowsScanned={}, plannedAssignments={}, "
                        + "updatedRows={}, invalidRows={}, legacyCollisionGroups={}, legacyCollisionRows={}, "
                        + "currentCollisionRows={}, committed={}, scannedBatchCount={}",
                result.mode(), result.legacyRowsScanned(), result.plannedAssignments(), result.updatedRows(),
                result.invalidRows(), result.legacyCollisionGroups(), result.legacyCollisionRows(),
                result.currentCollisionRows(), result.committed(), result.scannedBatchCount());
    }
}
