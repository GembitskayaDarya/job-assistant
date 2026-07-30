package com.darya.jobassistant.ai;

import com.darya.jobassistant.ai.config.JobAnalysisProperties;
import com.darya.jobassistant.ai.dto.AnalyzeVacancyResult;
import com.darya.jobassistant.ai.entity.AnalysisStatus;
import com.darya.jobassistant.ai.entity.JobAnalysisEntity;
import com.darya.jobassistant.ai.exception.JobAnalysisException;
import com.darya.jobassistant.ai.model.AnalysisOrigin;
import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.ai.model.JobAnalysisModelVersion;
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
 * <p>Every analysis this service starts or completes is {@link AnalysisOrigin#MANUAL}: both the
 * ordinary {@code /analyze} command and the guided-import "Analyze" callback go through this same
 * use case, so there is exactly one place that decides that origin.
 *
 * <p>A completed analysis whose {@code analysisVersion} is below {@link
 * JobAnalysisModelVersion#CURRENT} is safely recalculated here too (see {@link #acquireClaim}):
 * the row stays {@code COMPLETED} and its previous content stays fully readable for the entire
 * reanalysis attempt, via the repository's separate reanalysis-claim mechanism.
 *
 * <p>Deliberately has no class-level {@code @Transactional}, for the same reason as {@code
 * VacancyImportReviewService}: {@link JobAnalysisService#analyze} makes an external AI call, which
 * must never run inside an open database transaction. Every persistence step instead goes through
 * its own short {@link TransactionTemplate} call - see {@link #acquireClaim} (Transaction A: check
 * for an existing analysis or claim the vacancy) and {@link #runNewAnalysis}/{@link
 * #runReanalysis} (Transaction B: persist the result, only after the AI call - which sits between
 * the two - has returned).
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
            case ClaimOutcome.NewClaim ignored -> runNewAnalysis(vacancyId, jobOffer);
            case ClaimOutcome.ReanalysisClaim(var claimedAt) -> runReanalysis(vacancyId, jobOffer, claimedAt);
            case ClaimOutcome.AlreadyAvailable(var analysis) -> new AnalyzeVacancyResult.Available(jobOffer, analysis, false);
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
        Optional<JobAnalysisEntity> claimed =
                jobAnalysisRepository.claimIfAbsent(vacancyId, now, AnalysisOrigin.MANUAL, now);
        if (claimed.isPresent()) {
            return new ClaimOutcome.NewClaim();
        }

        JobAnalysisEntity current = jobAnalysisRepository.findByVacancyId(vacancyId)
                .orElseThrow(() -> new IllegalStateException(
                        "claimIfAbsent found an existing row for vacancy " + vacancyId + " but none could be loaded"));

        if (current.getStatus() == AnalysisStatus.COMPLETED) {
            return acquireForCompletedRow(vacancyId, current, now);
        }

        // IN_PROGRESS: only a claim whose updatedAt is older than the configured stale threshold
        // may be reclaimed - this is the bounded-timeout recovery path for an abandoned attempt
        // (e.g. the process that owned it crashed mid-call). A fresh IN_PROGRESS claim is left
        // alone entirely; duplicate concurrent AI calls for the same vacancy are prevented for as
        // long as the owning attempt keeps running.
        Instant staleThreshold = now.minus(properties.claimStaleAfter());
        boolean reclaimed = jobAnalysisRepository.reclaimStaleClaim(vacancyId, now, staleThreshold);
        return reclaimed ? new ClaimOutcome.NewClaim() : new ClaimOutcome.StillInProgress();
    }

    /**
     * A completed row at the current version is simply returned - but first stamped as manually
     * reviewed (if not already), since {@code /analyze} reusing it is itself the user's manual
     * review action, regardless of the row's original {@code analysisOrigin} (which is preserved
     * untouched - see {@code JobAnalysisRepository#markManuallyReviewedIfAbsent}). This runs inside
     * the same short transaction as the claim check itself (Transaction A in {@link #analyze}), no
     * AI call involved. An outdated row is safely recalculated via the reanalysis-claim mechanism
     * instead: if this call wins the claim, the old content stays put and readable while the AI
     * call runs (the claim itself also stamps the manual-review marker - see {@link
     * JobAnalysisRepository#attemptReanalysisClaim}); if another caller already owns a valid
     * (non-stale) reanalysis claim, or the row was upgraded to current between our read and this
     * attempt, the still-readable completed content is returned without calling AI again.
     */
    private ClaimOutcome acquireForCompletedRow(UUID vacancyId, JobAnalysisEntity current, Instant now) {
        if (current.getAnalysisVersion() >= JobAnalysisModelVersion.CURRENT) {
            jobAnalysisRepository.markManuallyReviewedIfAbsent(vacancyId, now);
            return new ClaimOutcome.AlreadyAvailable(JobAnalysisRepository.toDomain(current).analysis());
        }

        Instant staleThreshold = now.minus(properties.claimStaleAfter());
        boolean reanalysisClaimed = jobAnalysisRepository.attemptReanalysisClaim(vacancyId, now, staleThreshold);
        if (reanalysisClaimed) {
            return new ClaimOutcome.ReanalysisClaim(now);
        }

        JobAnalysisEntity latest = jobAnalysisRepository.findByVacancyId(vacancyId)
                .orElseThrow(() -> new IllegalStateException(
                        "Completed analysis for vacancy " + vacancyId + " disappeared during reanalysis claim attempt"));
        return new ClaimOutcome.AlreadyAvailable(JobAnalysisRepository.toDomain(latest).analysis());
    }

    /**
     * No transaction is open here: {@link JobAnalysisService#analyze} makes the external AI call,
     * strictly between the commit of {@link #acquireClaim}'s transaction and the start of
     * Transaction B below.
     */
    private AnalyzeVacancyResult runNewAnalysis(UUID vacancyId, JobOffer jobOffer) {
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
     * Mirrors {@link #runNewAnalysis} except failure never touches the row's existing completed
     * content: only the reanalysis claim fields are cleared, so the previous analysis, its
     * version and its origin are exactly as they were before this attempt started.
     */
    private AnalyzeVacancyResult runReanalysis(UUID vacancyId, JobOffer jobOffer, Instant claimedAt) {
        CandidateProfile profile = candidateProfileProvider.getProfile();
        JobAnalysis analysis;
        try {
            analysis = jobAnalysisService.analyze(profile, jobOffer);
        } catch (JobAnalysisException e) {
            log.warn("Vacancy reanalysis failed for vacancy {}", vacancyId, e);
            newTransaction.executeWithoutResult(status -> jobAnalysisRepository.releaseReanalysisClaim(vacancyId, claimedAt));
            return new AnalyzeVacancyResult.Failed();
        }

        Instant completedAt = Instant.now(clock);
        boolean completed = newTransaction.execute(
                status -> jobAnalysisRepository.completeReanalysis(vacancyId, analysis, claimedAt, completedAt));
        if (completed) {
            return new AnalyzeVacancyResult.Available(jobOffer, analysis, true);
        }
        return resolveAfterLostCompletion(vacancyId, jobOffer);
    }

    /**
     * Reached only if the claim this call owned was no longer valid by the time the result was
     * ready - not expected in normal operation, but handled defensively rather than discarding a
     * successful analysis: whatever is authoritative now is reloaded and returned.
     */
    private AnalyzeVacancyResult resolveAfterLostCompletion(UUID vacancyId, JobOffer jobOffer) {
        Optional<PersistedJobAnalysis> current = newTransaction.execute(status -> jobAnalysisRepository.findCompletedByVacancyId(vacancyId));
        return current
                .<AnalyzeVacancyResult>map(persisted -> new AnalyzeVacancyResult.Available(jobOffer, persisted.analysis(), false))
                .orElseGet(AnalyzeVacancyResult.Failed::new);
    }

    private sealed interface ClaimOutcome {
        record NewClaim() implements ClaimOutcome {
        }

        record ReanalysisClaim(Instant claimedAt) implements ClaimOutcome {
        }

        record AlreadyAvailable(JobAnalysis analysis) implements ClaimOutcome {
        }

        record StillInProgress() implements ClaimOutcome {
        }
    }
}
