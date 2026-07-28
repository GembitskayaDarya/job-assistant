package com.darya.jobassistant.vacancies.maintenance.canonicalurl;

import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Populates {@code canonical_url} for every legacy {@code Vacancy} ({@code canonical_url IS NULL})
 * that {@link VacancyCanonicalUrlLegacyPlanner} - the exact same planner {@code
 * VacancyCanonicalUrlAuditService} uses for its read-only report - classifies as safe. Never
 * touches {@code url}, never merges or deletes a row, and never writes a row the plan did not
 * classify as safe.
 *
 * <h2>DRY_RUN never writes</h2>
 *
 * {@link #dryRun()} runs the planner inside a {@code readOnly = true}, {@code REPEATABLE_READ}
 * transaction - the same isolation the audit uses, for the same reason (one consistent snapshot
 * across every page) - and returns a {@link VacancyCanonicalUrlBackfillResult} with {@code
 * updatedRows=0} and {@code committed=false}. It never opens a write transaction.
 *
 * <h2>APPLY is one all-or-nothing transaction</h2>
 *
 * {@link #apply()} runs entirely inside a single {@code REQUIRES_NEW}, {@code REPEATABLE_READ}
 * transaction, built with an explicit {@link TransactionTemplate} rather than {@code
 * @Transactional} - transaction ownership here must not depend on self-invocation, since a caller
 * (the runner) always invokes this method directly on the bean, but relying on annotation-based
 * AOP for a single-method "the whole operation is one transaction" contract is exactly the kind of
 * assumption that silently breaks the moment anyone refactors a call to go through {@code this.}
 * instead. Inside that one transaction:
 *
 * <ol>
 *   <li>A <em>fresh</em> plan is built - never the result of an earlier {@link #dryRun()} call -
 *       so a row that became unsafe (a concurrent write introduced a collision) between an
 *       operator running DRY_RUN and later running APPLY is never written anyway.
 *   <li>If the fresh plan has any blocker at all (an invalid source URL, a legacy-to-legacy
 *       collision, or a legacy-to-current collision), {@link VacancyCanonicalUrlBackfillBlockedException}
 *       is thrown immediately - zero {@code UPDATE} statements are issued, and the exception
 *       propagates out of the transaction callback, rolling it back (trivially, since nothing was
 *       written).
 *   <li>Otherwise, every safe assignment is written with {@link
 *       VacancyRepository#setCanonicalUrlIfNull}, one row at a time, each call's return value
 *       checked: exactly {@code 1} is the only acceptable outcome. Anything else - {@code 0} rows
 *       affected (the row was concurrently modified inside this transaction's own snapshot,
 *       which should be impossible under {@code REPEATABLE_READ} but is verified rather than
 *       assumed) or an unexpected {@link DataIntegrityViolationException} from {@code
 *       uk_vacancy_canonical_url} - is translated into {@link
 *       VacancyCanonicalUrlBackfillInvariantViolationException} and thrown, rolling back every
 *       update this run already made, not just the one that failed.
 *   <li>Only once every planned assignment has been written and confirmed does the method return
 *       normally, letting {@link TransactionTemplate#execute} commit. There is no partial commit
 *       path: either every safe row is updated, or none are.
 * </ol>
 *
 * <p>{@code url} is never part of the {@code UPDATE} - {@link VacancyRepository#setCanonicalUrlIfNull}
 * sets only {@code canonical_url} - so the original source URL is preserved byte-for-byte by
 * construction, not by a separate check.
 *
 * <h2>Scale</h2>
 *
 * This project's real dataset has single-digit-thousands of vacancies at most, so one transaction
 * covering every safe assignment is the simplest and safest choice - see the class Javadoc
 * rationale shared with {@link VacancyCanonicalUrlLegacyPlanner}. A dataset with millions of legacy
 * rows would need a resumable, staged backfill (bounded batches, each independently committed,
 * with progress tracked so a restart doesn't redo completed work) instead of one all-or-nothing
 * transaction; that framework is explicitly out of scope for this step.
 */
@Service
public class VacancyCanonicalUrlBackfillService {

    private final VacancyRepository vacancyRepository;
    private final VacancyCanonicalUrlLegacyPlanner planner;
    private final VacancyCanonicalUrlBackfillProperties properties;
    private final TransactionTemplate readOnlyTransaction;
    private final TransactionTemplate applyTransaction;

    public VacancyCanonicalUrlBackfillService(
            VacancyRepository vacancyRepository,
            VacancyCanonicalUrlBackfillProperties properties,
            PlatformTransactionManager transactionManager) {
        this.vacancyRepository = vacancyRepository;
        this.planner = new VacancyCanonicalUrlLegacyPlanner(vacancyRepository);
        this.properties = properties;

        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
        this.readOnlyTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);

        this.applyTransaction = new TransactionTemplate(transactionManager);
        this.applyTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.applyTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    /** Plans and reports only. Never opens a write transaction, never issues an {@code UPDATE}. */
    public VacancyCanonicalUrlBackfillResult dryRun() {
        VacancyCanonicalUrlLegacyPlan plan = readOnlyTransaction.execute(status -> planner.plan(properties.batchSize()));
        return buildResult(VacancyCanonicalUrlBackfillMode.DRY_RUN, plan, 0, false);
    }

    /**
     * Atomically backfills every currently-safe legacy row - see class javadoc for the full
     * transaction and rollback contract.
     *
     * @throws VacancyCanonicalUrlBackfillBlockedException if the freshly recomputed plan still has
     *     any invalid or colliding row; zero rows are updated
     * @throws VacancyCanonicalUrlBackfillInvariantViolationException if a write's own result
     *     contradicts the plan that was just computed inside the same transaction
     */
    public VacancyCanonicalUrlBackfillResult apply() {
        return applyTransaction.execute(status -> applyWithinTransaction());
    }

    private VacancyCanonicalUrlBackfillResult applyWithinTransaction() {
        VacancyCanonicalUrlLegacyPlan plan = planner.plan(properties.batchSize());
        if (plan.hasBlockers()) {
            throw blockedException(plan);
        }

        int updatedRows = 0;
        for (SafeCanonicalUrlAssignment assignment : plan.safeAssignments()) {
            int rowsAffected;
            try {
                rowsAffected = vacancyRepository.setCanonicalUrlIfNull(assignment.vacancyId(), assignment.canonicalUrl());
            } catch (DataIntegrityViolationException e) {
                throw new VacancyCanonicalUrlBackfillInvariantViolationException(
                        "uk_vacancy_canonical_url was unexpectedly violated while backfilling vacancy "
                                + assignment.vacancyId() + " - the database changed concurrently or the plan is no longer valid",
                        e);
            }
            if (rowsAffected != 1) {
                throw new VacancyCanonicalUrlBackfillInvariantViolationException(
                        "Expected exactly one row to update for vacancy " + assignment.vacancyId() + " but affected "
                                + rowsAffected + " - the database changed concurrently or the plan is no longer valid");
            }
            updatedRows++;
        }

        // Defensive, not merely aspirational: every successful setCanonicalUrlIfNull call already
        // proved its own row is no longer canonical_url IS NULL (the conditional UPDATE could not
        // have matched otherwise), so by construction no planned safe row remains null once this
        // loop completes without throwing - this check exists purely to catch a future refactor
        // that breaks that invariant, not because it can fail today.
        if (updatedRows != plan.safeAssignments().size()) {
            throw new VacancyCanonicalUrlBackfillInvariantViolationException(
                    "Updated row count " + updatedRows + " does not match planned assignment count "
                            + plan.safeAssignments().size());
        }

        return buildResult(VacancyCanonicalUrlBackfillMode.APPLY, plan, updatedRows, true);
    }

    private static final int MAX_SAMPLE_BLOCKING_VACANCY_IDS = 50;

    private VacancyCanonicalUrlBackfillBlockedException blockedException(VacancyCanonicalUrlLegacyPlan plan) {
        List<UUID> sample = new ArrayList<>();
        sample.addAll(plan.invalidSourceUrlVacancyIds());
        for (LegacyToLegacyCollisionGroup group : plan.legacyToLegacyCollisionGroups()) {
            sample.addAll(group.vacancyIds());
        }
        for (LegacyToCurrentCollision collision : plan.legacyToCurrentCollisions()) {
            sample.add(collision.vacancyId());
        }
        List<UUID> bounded = sample.size() > MAX_SAMPLE_BLOCKING_VACANCY_IDS
                ? sample.subList(0, MAX_SAMPLE_BLOCKING_VACANCY_IDS) : sample;

        String message = "Refusing to apply canonical URL backfill: invalidSourceUrlRows=" + plan.invalidSourceUrlVacancyIds().size()
                + ", legacyToLegacyCollisionRows=" + plan.legacyToLegacyCollisionRows()
                + ", legacyToCurrentCollisionRows=" + plan.legacyToCurrentCollisions().size();

        return new VacancyCanonicalUrlBackfillBlockedException(
                message,
                plan.invalidSourceUrlVacancyIds().size(),
                plan.legacyToLegacyCollisionRows(),
                plan.legacyToCurrentCollisions().size(),
                bounded);
    }

    private VacancyCanonicalUrlBackfillResult buildResult(
            VacancyCanonicalUrlBackfillMode mode, VacancyCanonicalUrlLegacyPlan plan, int updatedRows, boolean committed) {
        return new VacancyCanonicalUrlBackfillResult(
                mode,
                plan.totalLegacyRows(),
                plan.safeAssignments().size(),
                updatedRows,
                plan.invalidSourceUrlVacancyIds().size(),
                plan.legacyToLegacyCollisionGroups().size(),
                plan.legacyToLegacyCollisionRows(),
                plan.legacyToCurrentCollisions().size(),
                committed,
                plan.scannedBatchCount());
    }
}
