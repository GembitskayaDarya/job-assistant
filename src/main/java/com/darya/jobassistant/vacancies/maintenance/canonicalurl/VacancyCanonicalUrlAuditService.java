package com.darya.jobassistant.vacancies.maintenance.canonicalurl;

import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import com.darya.jobassistant.vacancies.url.VacancyUrlCanonicalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only classification of every legacy {@code Vacancy} (one with {@code canonical_url IS
 * NULL}) into {@code SAFE_TO_BACKFILL} or one of the {@link VacancyCanonicalUrlAuditIssueType}
 * categories, using {@link VacancyUrlCanonicalizer} as the sole canonicalization algorithm - the
 * exact same one {@code VacancyCreationService} uses for new rows, so a row classified safe here
 * would resolve to the identity a real backfill ({@code VacancyCanonicalUrlBackfillService})
 * would actually write.
 *
 * <p><b>This class never writes.</b> It never calls {@code save}, never issues {@code UPDATE}/
 * {@code DELETE}, never mutates a loaded row, and never derives-and-persists a {@code
 * canonical_url} as a side effect of reading one. See {@code VacancyCanonicalUrlAuditRunner} for
 * the only production caller.
 *
 * <p>The actual scan/classification work is delegated to {@link VacancyCanonicalUrlLegacyPlanner} -
 * the same planner {@code VacancyCanonicalUrlBackfillService} uses for DRY_RUN/APPLY - so there is
 * exactly one place the classification rules live; this class only shapes the resulting {@link
 * VacancyCanonicalUrlLegacyPlan} into the bounded, issue-oriented {@link
 * VacancyCanonicalUrlAuditReport} it has always returned.
 *
 * <h2>Transaction: one read-only snapshot for the whole run</h2>
 *
 * {@link #audit()} runs in a single {@code readOnly = true}, {@code REPEATABLE_READ} transaction -
 * not one transaction per page and not one per row - so every page of legacy rows and the
 * "currently populated" lookup are all read from the same consistent database snapshot, and a
 * concurrent write elsewhere (an import Save, an ingestion insert) can neither introduce nor
 * remove a collision partway through a run. {@code REPEATABLE_READ} does not by itself replace
 * operational care: production execution should still prefer a maintenance window, or at least
 * pausing ingestion/import writers, since a long-running snapshot on a busy table can still cause
 * lock waits or bloat even though it cannot corrupt this audit's own result.
 */
@Service
public class VacancyCanonicalUrlAuditService {

    private final VacancyCanonicalUrlLegacyPlanner planner;
    private final VacancyCanonicalUrlAuditProperties properties;

    public VacancyCanonicalUrlAuditService(VacancyRepository vacancyRepository, VacancyCanonicalUrlAuditProperties properties) {
        this.planner = new VacancyCanonicalUrlLegacyPlanner(vacancyRepository);
        this.properties = properties;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public VacancyCanonicalUrlAuditReport audit() {
        VacancyCanonicalUrlLegacyPlan plan = planner.plan(properties.batchSize());
        return buildReport(plan);
    }

    private VacancyCanonicalUrlAuditReport buildReport(VacancyCanonicalUrlLegacyPlan plan) {
        List<VacancyCanonicalUrlAuditIssue> allIssues = new ArrayList<>();

        for (UUID vacancyId : plan.invalidSourceUrlVacancyIds()) {
            allIssues.add(new VacancyCanonicalUrlAuditIssue(
                    vacancyId, VacancyCanonicalUrlAuditIssueType.INVALID_SOURCE_URL, null, List.of()));
        }
        for (LegacyToLegacyCollisionGroup group : plan.legacyToLegacyCollisionGroups()) {
            for (UUID vacancyId : group.vacancyIds()) {
                List<UUID> related = group.vacancyIds().stream().filter(id -> !id.equals(vacancyId)).toList();
                allIssues.add(new VacancyCanonicalUrlAuditIssue(
                        vacancyId, VacancyCanonicalUrlAuditIssueType.LEGACY_TO_LEGACY_COLLISION, group.canonicalUrl(), related));
            }
        }
        for (LegacyToCurrentCollision collision : plan.legacyToCurrentCollisions()) {
            allIssues.add(new VacancyCanonicalUrlAuditIssue(
                    collision.vacancyId(), VacancyCanonicalUrlAuditIssueType.LEGACY_TO_CURRENT_COLLISION,
                    collision.canonicalUrl(), List.of(collision.currentOwnerVacancyId())));
        }

        int maxReportedIssues = properties.maxReportedIssues();
        List<VacancyCanonicalUrlAuditIssue> bounded =
                allIssues.size() > maxReportedIssues ? allIssues.subList(0, maxReportedIssues) : allIssues;
        int omittedIssueCount = Math.max(0, allIssues.size() - maxReportedIssues);

        return new VacancyCanonicalUrlAuditReport(
                plan.totalLegacyRows(),
                plan.safeAssignments().size(),
                plan.invalidSourceUrlVacancyIds().size(),
                plan.legacyToLegacyCollisionGroups().size(),
                plan.legacyToLegacyCollisionRows(),
                plan.legacyToCurrentCollisions().size(),
                plan.scannedBatchCount(),
                omittedIssueCount,
                bounded);
    }
}
