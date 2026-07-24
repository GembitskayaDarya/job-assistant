package com.darya.jobassistant.ai;

import com.darya.jobassistant.ai.config.JobAnalysisProperties;
import com.darya.jobassistant.ai.dto.AnalyzeVacancyResult;
import com.darya.jobassistant.ai.entity.AnalysisStatus;
import com.darya.jobassistant.ai.entity.JobAnalysisEntity;
import com.darya.jobassistant.ai.exception.JobAnalysisException;
import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.ai.model.PersistedJobAnalysis;
import com.darya.jobassistant.ai.repository.JobAnalysisRepository;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateProfileProvider;
import com.darya.jobassistant.exception.VacancyNotFoundException;
import com.darya.jobassistant.integrations.ai.openai.JobAnalysisService;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.mapper.VacancyJobOfferMapper;
import com.darya.jobassistant.vacancies.service.VacancyQueryService;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reuses the existing {@link JobAnalysisService} (prompt, port, validation) and {@link
 * JobAnalysisRepository} unchanged - this class only adds the "check/claim before calling AI,
 * persist after" orchestration and concurrency protection {@code /analyze} never needed on its
 * own (a single interactive command has no concurrent-caller problem; a Telegram callback that
 * any owner of the chat can press, possibly more than once in quick succession, does).
 *
 * <p>Deliberately has no class-level {@code @Transactional}, for the same reason as {@code
 * VacancyImportReviewService}: {@link JobAnalysisService#analyze} makes an external AI call, which
 * must never run inside an open database transaction. Every persistence step instead goes through
 * its own short {@link TransactionTemplate} call - see {@link #acquireClaim} (Transaction A: check
 * for an existing analysis or claim the vacancy) and {@link #runAnalysis} (Transaction B: persist
 * the result, only after the AI call - which sits between the two - has returned).
 */
@Slf4j
@Service
public class AnalyzeVacancyService implements AnalyzeVacancyUseCase {

    private final VacancyQueryService vacancyQueryService;
    private final VacancyJobOfferMapper vacancyJobOfferMapper;
    private final CandidateProfileProvider candidateProfileProvider;
    private final JobAnalysisService jobAnalysisService;
    private final JobAnalysisRepository jobAnalysisRepository;
    private final Clock clock;
    private final JobAnalysisProperties properties;
    private final TransactionTemplate newTransaction;

    public AnalyzeVacancyService(
            VacancyQueryService vacancyQueryService,
            VacancyJobOfferMapper vacancyJobOfferMapper,
            CandidateProfileProvider candidateProfileProvider,
            JobAnalysisService jobAnalysisService,
            JobAnalysisRepository jobAnalysisRepository,
            Clock clock,
            JobAnalysisProperties properties,
            PlatformTransactionManager transactionManager) {
        this.vacancyQueryService = vacancyQueryService;
        this.vacancyJobOfferMapper = vacancyJobOfferMapper;
        this.candidateProfileProvider = candidateProfileProvider;
        this.jobAnalysisService = jobAnalysisService;
        this.jobAnalysisRepository = jobAnalysisRepository;
        this.clock = clock;
        this.properties = properties;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public AnalyzeVacancyResult analyze(UUID vacancyId) {
        Vacancy vacancy;
        try {
            vacancy = vacancyQueryService.getById(vacancyId);
        } catch (VacancyNotFoundException e) {
            return new AnalyzeVacancyResult.VacancyNotFound();
        }
        JobOffer jobOffer = vacancyJobOfferMapper.toJobOffer(vacancy);

        ClaimOutcome claim = newTransaction.execute(status -> acquireClaim(vacancyId));
        return switch (claim) {
            case ClaimOutcome.Owned ignored -> runAnalysis(vacancyId, jobOffer);
            case ClaimOutcome.AlreadyCompleted(var analysis) -> new AnalyzeVacancyResult.Available(jobOffer, analysis, false);
            case ClaimOutcome.StillInProgress ignored -> new AnalyzeVacancyResult.InProgress();
        };
    }

    /**
     * Transaction A. {@link JobAnalysisRepository#claimIfAbsent} does the "does an analysis
     * already exist" check and the claim insert as a single atomic database operation (via the
     * unique constraint on {@code vacancy_id}), so there is no separate exists()-then-insert()
     * race window here to protect against.
     */
    private ClaimOutcome acquireClaim(UUID vacancyId) {
        Instant now = Instant.now(clock);
        Optional<JobAnalysisEntity> claimed = jobAnalysisRepository.claimIfAbsent(vacancyId, now);
        if (claimed.isPresent()) {
            return new ClaimOutcome.Owned();
        }

        JobAnalysisEntity current = jobAnalysisRepository.findByVacancyId(vacancyId)
                .orElseThrow(() -> new IllegalStateException(
                        "claimIfAbsent found an existing row for vacancy " + vacancyId + " but none could be loaded"));
        if (current.getStatus() == AnalysisStatus.COMPLETED) {
            return new ClaimOutcome.AlreadyCompleted(JobAnalysisRepository.toDomain(current).analysis());
        }

        // IN_PROGRESS: only a claim whose updatedAt is older than the configured stale threshold
        // may be reclaimed - this is the bounded-timeout recovery path for an abandoned attempt
        // (e.g. the process that owned it crashed mid-call). A fresh IN_PROGRESS claim is left
        // alone entirely; duplicate concurrent AI calls for the same vacancy are prevented for as
        // long as the owning attempt keeps running.
        Instant staleThreshold = now.minus(properties.claimStaleAfter());
        boolean reclaimed = jobAnalysisRepository.reclaimStaleClaim(vacancyId, now, staleThreshold);
        return reclaimed ? new ClaimOutcome.Owned() : new ClaimOutcome.StillInProgress();
    }

    /**
     * No transaction is open here: {@link JobAnalysisService#analyze} makes the external AI call,
     * strictly between the commit of {@link #acquireClaim}'s transaction and the start of
     * Transaction B below.
     */
    private AnalyzeVacancyResult runAnalysis(UUID vacancyId, JobOffer jobOffer) {
        CandidateProfile profile = candidateProfileProvider.getProfile();
        JobAnalysis analysis;
        try {
            analysis = jobAnalysisService.analyze(profile, jobOffer);
        } catch (JobAnalysisException e) {
            log.warn("Vacancy analysis failed for vacancy {}", vacancyId, e);
            newTransaction.executeWithoutResult(status -> jobAnalysisRepository.releaseClaim(vacancyId));
            return new AnalyzeVacancyResult.Failed();
        }

        // Transaction B.
        Instant completedAt = Instant.now(clock);
        boolean completed = newTransaction.execute(status -> jobAnalysisRepository.completeClaim(vacancyId, analysis, completedAt));
        if (completed) {
            return new AnalyzeVacancyResult.Available(jobOffer, analysis, true);
        }
        return resolveAfterLostCompletion(vacancyId, jobOffer);
    }

    /**
     * Reached only if the claim this call owned was no longer {@code IN_PROGRESS} by the time the
     * result was ready - not expected in normal operation (nothing else can complete or release a
     * claim this call exclusively owns), but handled defensively rather than discarding a
     * successful analysis: whatever is authoritative now is reloaded and returned.
     */
    private AnalyzeVacancyResult resolveAfterLostCompletion(UUID vacancyId, JobOffer jobOffer) {
        Optional<PersistedJobAnalysis> current = newTransaction.execute(status -> jobAnalysisRepository.findCompletedByVacancyId(vacancyId));
        return current
                .<AnalyzeVacancyResult>map(persisted -> new AnalyzeVacancyResult.Available(jobOffer, persisted.analysis(), false))
                .orElseGet(AnalyzeVacancyResult.Failed::new);
    }

    private sealed interface ClaimOutcome {
        record Owned() implements ClaimOutcome {
        }

        record AlreadyCompleted(JobAnalysis analysis) implements ClaimOutcome {
        }

        record StillInProgress() implements ClaimOutcome {
        }
    }
}
