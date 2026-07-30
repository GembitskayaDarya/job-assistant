package com.darya.jobassistant.vacancies.service;

import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.vacancies.dto.VacancyCreationCommand;
import com.darya.jobassistant.vacancies.dto.VacancyCreationResult;
import com.darya.jobassistant.vacancies.dto.VacancyIngestionResult;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.policy.JobOfferMatchPolicy;
import com.darya.jobassistant.vacancyrecommendation.repository.VacancyRecommendationTaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * Owns one physical transaction per {@link JobOffer}, not one for a whole {@link #ingest} batch.
 * {@link VacancyCreationService#createIfAbsent} only ever joins an already-active transaction
 * ({@code Propagation.MANDATORY}) - it does not start one itself - so this class is what starts a
 * fresh {@code REQUIRES_NEW} transaction for each item and commits it before moving to the next.
 *
 * <p>That per-item boundary is deliberate: a single bad or duplicate offer must never roll back
 * vacancies already committed for earlier offers in the same batch, and a batch-wide transaction
 * would also hold one database connection open for the entire ingestion run for no benefit, since
 * each offer's creation is already fully self-contained.
 *
 * <p>A canonical-URL race (see {@link CanonicalVacancyCreationConflictException}) is retried at
 * most once per item, in a brand-new {@code REQUIRES_NEW} transaction - by that point the
 * competing insert has committed, so the retry's {@link VacancyCreationService#createIfAbsent}
 * simply finds it and reports {@code newlyCreated() == false}. A second conflict on the retry is
 * not retried again; it is left to propagate to the caller, matching this class's existing
 * per-item failure handling ({@link #ingest} skips it and logs a warning; {@link #persist}
 * propagates it).
 */
@Slf4j
@Service
public class VacancyIngestionService {

    private final VacancyCreationService vacancyCreationService;
    private final JobOfferMatchPolicy jobOfferMatchPolicy;
    private final VacancyRecommendationTaskRepository vacancyRecommendationTaskRepository;
    private final Clock clock;
    private final TransactionTemplate perItemTransaction;

    public VacancyIngestionService(
            VacancyCreationService vacancyCreationService,
            JobOfferMatchPolicy jobOfferMatchPolicy,
            VacancyRecommendationTaskRepository vacancyRecommendationTaskRepository,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.vacancyCreationService = vacancyCreationService;
        this.jobOfferMatchPolicy = jobOfferMatchPolicy;
        this.vacancyRecommendationTaskRepository = vacancyRecommendationTaskRepository;
        this.clock = clock;
        this.perItemTransaction = new TransactionTemplate(transactionManager);
        this.perItemTransaction.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Deduplicates by canonical URL (see {@link VacancyCreationService}): an existing vacancy for
     * an equivalent URL is returned as-is, never updated - matching the ingestion job's original
     * upsert-free behavior.
     */
    public Vacancy persist(JobOffer jobOffer) {
        return createWithRetry(buildCreationCommand(jobOffer)).vacancy();
    }

    /**
     * Discovery-specific atomic persistence entry point - the only caller is {@code
     * JobDiscoveryService}, whose extracted vacancy data (location, remote mode, salary, posted
     * date) {@link #persist(JobOffer)} has no way to carry. Reuses the exact same per-item {@code
     * REQUIRES_NEW} transaction and canonical-conflict retry-once behavior as {@link
     * #persist(JobOffer)}/{@link #ingest} - {@code createWithRetry} is the one implementation of
     * that transaction boundary in the whole project; this method does not duplicate it. Unlike
     * {@link #persist(JobOffer)}, the full {@link VacancyCreationResult} is returned (not just the
     * {@code Vacancy}) so the caller can distinguish a newly created vacancy from one that already
     * existed.
     *
     * <p>When the result is {@code CREATED}, exactly one {@code VacancyRecommendationTask} in
     * {@code PENDING} status is inserted for it inside this same transaction, atomically - see
     * {@link #createAndRegisterTask}. A durable task therefore either commits together with its
     * {@code Vacancy}, or neither commits at all: task-insert failure (including the structurally
     * unreachable {@code uk_vacancy_recommendation_task_vacancy} conflict) propagates out of this
     * transaction exactly like any other failure inside {@link
     * VacancyCreationService#createIfAbsent} would, rolling the {@code Vacancy} insert back with
     * it. When the result is {@code ALREADY_EXISTS} (including after a canonical-conflict retry),
     * no task is created and no existing vacancy's task is touched or inferred.
     */
    public VacancyCreationResult persistDiscovered(VacancyCreationCommand command) {
        return createWithRetry(command, true);
    }

    /**
     * Batch counterpart used by automatic ingestion. Novelty is decided by {@link
     * VacancyCreationService#createIfAbsent}, which is safe under concurrent ingestion callers -
     * see that class for how the application-level canonical lookup and the database's partial
     * unique index combine to guarantee that.
     *
     * <p>Every fetched offer is checked against {@link JobOfferMatchPolicy} before persistence;
     * offers that don't match are skipped without creating a {@code Company} or {@code Vacancy}
     * record. {@code fetchedCount} still reflects every offer passed in, so after filtering
     * {@code fetchedCount == newlyPersisted + alreadyKnown} is no longer guaranteed - offers may
     * have been filtered out or failed during per-offer processing. {@code persisted} only ever
     * contains vacancies from committed, per-item transactions - never a row from an item whose
     * transaction was rolled back.
     */
    public VacancyIngestionResult ingest(List<JobOffer> jobOffers) {
        List<Vacancy> persisted = new ArrayList<>();
        int alreadyKnown = 0;
        for (JobOffer jobOffer : jobOffers) {
            if (!jobOfferMatchPolicy.matches(jobOffer)) {
                log.debug("Filtered out job offer from {} (id={}, url={}): \"{}\"",
                        jobOffer.source(), jobOffer.id(), jobOffer.url(), jobOffer.title());
                continue;
            }
            try {
                VacancyCreationResult outcome = createWithRetry(buildCreationCommand(jobOffer));
                if (outcome.newlyCreated()) {
                    persisted.add(outcome.vacancy());
                } else {
                    alreadyKnown++;
                }
            } catch (RuntimeException e) {
                log.warn("Skipping job offer \"{}\" from {} - failed to persist: {}",
                        jobOffer.title(), jobOffer.source(), e.getMessage());
            }
        }
        return new VacancyIngestionResult(jobOffers.size(), persisted, alreadyKnown);
    }

    private VacancyCreationResult createWithRetry(VacancyCreationCommand command) {
        return createWithRetry(command, false);
    }

    private VacancyCreationResult createWithRetry(VacancyCreationCommand command, boolean registerRecommendationTask) {
        try {
            return perItemTransaction.execute(status -> createAndRegisterTask(command, registerRecommendationTask));
        } catch (CanonicalVacancyCreationConflictException e) {
            log.debug("Lost a canonical URL race for \"{}\"; retrying once in a fresh transaction",
                    command.title());
            return perItemTransaction.execute(status -> createAndRegisterTask(command, registerRecommendationTask));
        }
    }

    /**
     * Runs inside {@code perItemTransaction} - see {@link #createWithRetry(VacancyCreationCommand,
     * boolean)}. Task registration happens only for a {@code newlyCreated} result: the losing side
     * of a canonical-URL race resolves to {@code ALREADY_EXISTS} on retry (see {@link
     * VacancyIngestionService class javadoc}) and must never register a second task for a vacancy
     * some other, already-committed run already owns.
     */
    private VacancyCreationResult createAndRegisterTask(VacancyCreationCommand command, boolean registerRecommendationTask) {
        VacancyCreationResult result = vacancyCreationService.createIfAbsent(command);
        if (registerRecommendationTask && result.newlyCreated()) {
            vacancyRecommendationTaskRepository.createPending(result.vacancy().getId(), Instant.now(clock));
        }
        return result;
    }

    private VacancyCreationCommand buildCreationCommand(JobOffer jobOffer) {
        if (!StringUtils.hasText(jobOffer.url())) {
            throw new IllegalArgumentException("Job offer has no URL, cannot persist: " + jobOffer.title());
        }
        return new VacancyCreationCommand(
                jobOffer.company(),
                jobOffer.title(),
                jobOffer.description(),
                jobOffer.url(),
                null,
                null,
                null,
                null,
                null,
                null,
                jobOffer.source(),
                null);
    }
}
