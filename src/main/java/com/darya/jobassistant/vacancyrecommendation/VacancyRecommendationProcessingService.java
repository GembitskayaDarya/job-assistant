package com.darya.jobassistant.vacancyrecommendation;

import com.darya.jobassistant.ai.entity.AnalysisStatus;
import com.darya.jobassistant.ai.entity.JobAnalysisEntity;
import com.darya.jobassistant.ai.exception.JobAnalysisException;
import com.darya.jobassistant.ai.model.AnalysisOrigin;
import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.ai.model.PersistedJobAnalysis;
import com.darya.jobassistant.ai.repository.JobAnalysisRepository;
import com.darya.jobassistant.candidatecontext.CandidateContextProvider;
import com.darya.jobassistant.candidatecontext.CandidateContextSnapshot;
import com.darya.jobassistant.candidatecontext.analysis.CandidateContextForAnalysis;
import com.darya.jobassistant.candidatecontext.analysis.CandidateContextForAnalysisSelector;
import com.darya.jobassistant.integrations.ai.openai.JobAnalysisService;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.integrations.notifier.CompactVacancyRecommendation;
import com.darya.jobassistant.integrations.notifier.JobNotificationException;
import com.darya.jobassistant.integrations.notifier.JobNotificationFactory;
import com.darya.jobassistant.integrations.notifier.JobNotificationFailureType;
import com.darya.jobassistant.integrations.notifier.JobNotificationPort;
import com.darya.jobassistant.integrations.notifier.JobNotificationResult;
import com.darya.jobassistant.notifications.dto.NotificationDelivery;
import com.darya.jobassistant.notifications.dto.NotificationDeliveryTransitionResult;
import com.darya.jobassistant.notifications.dto.NotificationReservationResult;
import com.darya.jobassistant.notifications.repository.NotificationDeliveryRepository;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.mapper.VacancyJobOfferMapper;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import com.darya.jobassistant.vacancyrecommendation.config.RecommendationPolicyProperties;
import com.darya.jobassistant.vacancyrecommendation.config.VacancyRecommendationProperties;
import com.darya.jobassistant.vacancyrecommendation.entity.VacancyRecommendationTaskEntity;
import com.darya.jobassistant.vacancyrecommendation.repository.VacancyRecommendationTaskRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Processes {@code VacancyRecommendationTask}s claimed from the durable backlog atomically
 * registered by {@code VacancyIngestionService#persistDiscovered}:
 * <pre>
 * claim a bounded batch -&gt; for each task, sequentially: check manual review/existing analysis
 * -&gt; obtain analysis (reuse or exactly one new AI call) -&gt; recheck manual review -&gt; score policy
 * -&gt; recheck manual review -&gt; reserve/send/mark notification -&gt; complete/retry/dead
 * </pre>
 *
 * <p>Deliberately not {@code @Transactional}: it makes external AI ({@link JobAnalysisService}) and
 * Telegram ({@link JobNotificationPort}) calls, which must never run inside an open database
 * transaction. Every persistence step instead goes through its own short {@link
 * #newTransaction}, matching {@code AnalyzeVacancyService}/{@code JobMonitoringService}'s
 * convention - never one transaction around a whole task, let alone a whole batch.
 *
 * <h2>The manual-review concurrency boundary</h2>
 *
 * Manual review is rechecked at three points: before starting analysis, immediately after
 * obtaining it (fresh or reused), and immediately before reserving notification delivery. This
 * makes the window in which a concurrent {@code /analyze} could go unnoticed as small as this
 * process can make it - but it cannot be closed to zero: if a manual review commits in the
 * instant between this task's last recheck and {@link NotificationDeliveryRepository#reserve}
 * successfully reserving a {@code PENDING} row, the automatic delivery proceeds anyway. This is a
 * deliberate, documented limit, not an oversight - once notification delivery has been reserved,
 * suppressing it would require either locking the {@code job_analysis} row for the duration of a
 * Telegram call (violating "no external call inside a transaction") or a distributed lock this
 * step does not introduce. Exactly-once suppression after an external Telegram send has begun is
 * never claimed anywhere in this class.
 *
 * <h2>At-least-once notification delivery</h2>
 *
 * If Telegram accepts a message and this process crashes before {@code markSent} commits, a later
 * retry may send a duplicate: PostgreSQL and Telegram share no transactional commit. This is a
 * bounded at-least-once guarantee, not exactly-once - matching the existing {@code
 * NotificationDelivery} model's own limitation (see {@code JobMonitoringService}), never claimed to
 * be stronger here.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "vacancy-recommendation", name = "enabled", havingValue = "true")
public class VacancyRecommendationProcessingService {

    private final VacancyRecommendationTaskRepository taskRepository;
    private final VacancyRepository vacancyRepository;
    private final JobAnalysisRepository jobAnalysisRepository;
    private final VacancyJobOfferMapper vacancyJobOfferMapper;
    private final CandidateContextProvider candidateContextProvider;
    private final CandidateContextForAnalysisSelector candidateContextForAnalysisSelector;
    private final JobAnalysisService jobAnalysisService;
    private final JobNotificationFactory jobNotificationFactory;
    private final JobNotificationPort jobNotificationPort;
    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final RecommendationPolicyProperties policyProperties;
    private final VacancyRecommendationProperties properties;
    private final Clock clock;
    private final TransactionTemplate newTransaction;
    private final String instanceId = UUID.randomUUID().toString();

    public VacancyRecommendationProcessingService(
            VacancyRecommendationTaskRepository taskRepository,
            VacancyRepository vacancyRepository,
            JobAnalysisRepository jobAnalysisRepository,
            VacancyJobOfferMapper vacancyJobOfferMapper,
            CandidateContextProvider candidateContextProvider,
            CandidateContextForAnalysisSelector candidateContextForAnalysisSelector,
            JobAnalysisService jobAnalysisService,
            JobNotificationFactory jobNotificationFactory,
            JobNotificationPort jobNotificationPort,
            NotificationDeliveryRepository notificationDeliveryRepository,
            RecommendationPolicyProperties policyProperties,
            VacancyRecommendationProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.taskRepository = taskRepository;
        this.vacancyRepository = vacancyRepository;
        this.jobAnalysisRepository = jobAnalysisRepository;
        this.vacancyJobOfferMapper = vacancyJobOfferMapper;
        this.candidateContextProvider = candidateContextProvider;
        this.candidateContextForAnalysisSelector = candidateContextForAnalysisSelector;
        this.jobAnalysisService = jobAnalysisService;
        this.jobNotificationFactory = jobNotificationFactory;
        this.jobNotificationPort = jobNotificationPort;
        this.notificationDeliveryRepository = notificationDeliveryRepository;
        this.policyProperties = policyProperties;
        this.properties = properties;
        this.clock = clock;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public VacancyRecommendationProcessingResult processPending() {
        Instant startedAt = clock.instant();
        VacancyRecommendationProperties.Processing limits = properties.processing();
        RunAccumulator acc = new RunAccumulator();

        ClaimBatch batch = claimBatch(limits.batchSize(), limits.leaseDuration());
        acc.claimedTasks = batch.claimed().size();
        acc.leaseRecoveredTasks = batch.leaseRecoveredCount();

        for (VacancyRecommendationTaskEntity task : batch.claimed()) {
            try {
                processOne(task, acc);
            } catch (RuntimeException e) {
                log.error("Unexpected failure processing recommendation task {} (vacancy {})",
                        task.getId(), task.getVacancyId(), e);
                acc.addIssue(VacancyRecommendationIssueCategory.ANALYSIS_FAILED, task.getId(), task.getVacancyId(),
                        e.getClass().getSimpleName());
                handleRecoverableFailure(task, VacancyRecommendationFailureCategory.INTERNAL_ERROR, acc);
            }
        }

        Instant completedAt = clock.instant();
        VacancyRecommendationProcessingResult result =
                acc.toResult(startedAt, completedAt, Duration.between(startedAt, completedAt));
        logSummary(result);
        return result;
    }

    // --- Claim -----------------------------------------------------------------------------

    private ClaimBatch claimBatch(int batchSize, Duration leaseDuration) {
        ClaimBatch result = newTransaction.execute(status -> {
            List<VacancyRecommendationTaskEntity> preClaim = taskRepository.selectClaimCandidates(batchSize);
            if (preClaim.isEmpty()) {
                return new ClaimBatch(List.of(), 0);
            }
            int leaseRecoveredCount = (int) preClaim.stream()
                    .filter(t -> t.getStatus() == VacancyRecommendationTaskStatus.PROCESSING)
                    .count();
            List<UUID> ids = preClaim.stream().map(VacancyRecommendationTaskEntity::getId).toList();
            List<VacancyRecommendationTaskEntity> claimed =
                    taskRepository.claimByIds(ids, leaseDuration.toSeconds(), instanceId);
            return new ClaimBatch(claimed, leaseRecoveredCount);
        });
        return result == null ? new ClaimBatch(List.of(), 0) : result;
    }

    // --- Per-task orchestration --------------------------------------------------------------

    private void processOne(VacancyRecommendationTaskEntity task, RunAccumulator acc) {
        UUID vacancyId = task.getVacancyId();

        Optional<JobAnalysisEntity> existingEntity =
                newTransaction.execute(status -> jobAnalysisRepository.findByVacancyId(vacancyId));
        if (existingEntity != null && existingEntity.isPresent()) {
            JobAnalysisEntity entity = existingEntity.get();
            if (entity.getStatus() == AnalysisStatus.IN_PROGRESS) {
                // Boundary 1 (implicit): someone else - most likely a concurrent manual /analyze -
                // is actively analyzing this vacancy right now. Defer rather than guess; a later
                // retry re-evaluates once that claim resolves one way or another.
                handleRecoverableFailure(task, VacancyRecommendationFailureCategory.DATABASE_CONFLICT, acc);
                return;
            }
            handleExistingAnalysis(task, JobAnalysisRepository.toDomain(entity), acc);
            return;
        }

        AnalysisAttempt attempt = obtainAutomaticAnalysis(vacancyId, acc);
        switch (attempt) {
            case AnalysisAttempt.Fresh fresh -> proceedAfterAnalysis(task, vacancyId, fresh.analysis(), acc);
            case AnalysisAttempt.ExistingWinner winner -> handleExistingAnalysis(task, winner.analysis(), acc);
            case AnalysisAttempt.StillInProgress ignored ->
                    handleRecoverableFailure(task, VacancyRecommendationFailureCategory.DATABASE_CONFLICT, acc);
            case AnalysisAttempt.Failed failed -> {
                acc.analysisFailures++;
                acc.addIssue(VacancyRecommendationIssueCategory.ANALYSIS_FAILED, task.getId(), vacancyId,
                        failed.category().name());
                if (failed.category() == VacancyRecommendationFailureCategory.ANALYSIS_INVALID_RESPONSE) {
                    handleNonRecoverableFailure(task, failed.category(), acc);
                } else {
                    handleRecoverableFailure(task, failed.category(), acc);
                }
            }
        }
    }

    /**
     * Boundary 1 (explicit): an analysis already exists for this vacancy - decide what that means
     * before ever considering a new AI call. {@code origin=MANUAL} or a set {@code
     * manuallyReviewedAt} always suppresses; a non-{@code AUTOMATIC_DISCOVERY} origin (today,
     * {@code LEGACY} or {@code MONITORING}) is a different automatic workflow's analysis that this
     * pipeline must never notify from; only {@code AUTOMATIC_DISCOVERY} is reused.
     */
    private void handleExistingAnalysis(VacancyRecommendationTaskEntity task, PersistedJobAnalysis existing, RunAccumulator acc) {
        if (existing.isManuallyReviewed()) {
            completeTask(task, VacancyRecommendationTaskOutcome.MANUALLY_REVIEWED, acc);
            acc.manuallyReviewedTasks++;
            return;
        }
        if (existing.analysisOrigin() != AnalysisOrigin.AUTOMATIC_DISCOVERY) {
            completeTask(task, VacancyRecommendationTaskOutcome.ANALYSIS_ALREADY_EXISTS_NON_AUTOMATIC, acc);
            return;
        }
        acc.analysisReused++;
        proceedAfterAnalysis(task, task.getVacancyId(), existing.analysis(), acc);
    }

    /**
     * No analysis exists yet: claims the right to create one (via the same unique-constraint-backed
     * mechanism {@code AnalyzeVacancyService} uses), calls AI exactly once outside any transaction,
     * and persists the result with {@code origin=AUTOMATIC_DISCOVERY}. If the claim is lost to a
     * concurrent writer (almost always a manual {@code /analyze}), reloads and reports the winner
     * instead of invoking AI - the already-consumed claim slot cannot be refunded, but no second AI
     * call is made.
     */
    private AnalysisAttempt obtainAutomaticAnalysis(UUID vacancyId, RunAccumulator acc) {
        Instant claimedAt = clock.instant();
        Optional<JobAnalysisEntity> claimed = newTransaction.execute(status ->
                jobAnalysisRepository.claimIfAbsent(vacancyId, claimedAt, AnalysisOrigin.AUTOMATIC_DISCOVERY, null));
        if (claimed == null || claimed.isEmpty()) {
            return reloadAfterLostClaim(vacancyId);
        }

        acc.analysisAttempts++;
        Vacancy vacancy = loadVacancyOrThrow(vacancyId);
        JobOffer jobOffer = vacancyJobOfferMapper.toJobOffer(vacancy);
        CandidateContextSnapshot candidateContext = candidateContextProvider.loadCurrentContext();
        CandidateContextForAnalysis analysisContext = candidateContextForAnalysisSelector.select(candidateContext, jobOffer);

        JobAnalysis analysis;
        try {
            analysis = jobAnalysisService.analyze(analysisContext, jobOffer);
        } catch (JobAnalysisException e) {
            newTransaction.executeWithoutResult(status -> jobAnalysisRepository.releaseClaim(vacancyId));
            return new AnalysisAttempt.Failed(classifyAnalysisFailure(e));
        }

        Instant completedAt = clock.instant();
        Boolean completed = newTransaction.execute(status -> jobAnalysisRepository.completeClaim(vacancyId, analysis, completedAt));
        if (Boolean.TRUE.equals(completed)) {
            return new AnalysisAttempt.Fresh(analysis);
        }
        // Not expected in normal operation - mirrors AnalyzeVacancyService's own defensive fallback:
        // whatever is authoritative now is reloaded rather than discarding a successful AI result.
        return reloadAfterLostClaim(vacancyId);
    }

    private AnalysisAttempt reloadAfterLostClaim(UUID vacancyId) {
        Optional<JobAnalysisEntity> current = newTransaction.execute(status -> jobAnalysisRepository.findByVacancyId(vacancyId));
        if (current == null || current.isEmpty()) {
            return new AnalysisAttempt.Failed(VacancyRecommendationFailureCategory.INTERNAL_ERROR);
        }
        JobAnalysisEntity entity = current.get();
        if (entity.getStatus() == AnalysisStatus.IN_PROGRESS) {
            return new AnalysisAttempt.StillInProgress();
        }
        return new AnalysisAttempt.ExistingWinner(JobAnalysisRepository.toDomain(entity));
    }

    private void proceedAfterAnalysis(VacancyRecommendationTaskEntity task, UUID vacancyId, JobAnalysis analysis, RunAccumulator acc) {
        // Boundary 2: recheck manual review immediately after obtaining the analysis.
        if (isNowManuallyReviewed(vacancyId)) {
            completeTask(task, VacancyRecommendationTaskOutcome.MANUALLY_REVIEWED, acc);
            acc.manuallyReviewedTasks++;
            return;
        }

        if (analysis.score() < policyProperties.minimumScore()) {
            completeTask(task, VacancyRecommendationTaskOutcome.BELOW_SCORE_THRESHOLD, acc);
            acc.belowThresholdTasks++;
            return;
        }

        // Boundary 3: immediately before reserving notification delivery - see class javadoc for
        // why this is the last point at which suppression is possible.
        if (isNowManuallyReviewed(vacancyId)) {
            completeTask(task, VacancyRecommendationTaskOutcome.MANUALLY_REVIEWED, acc);
            acc.manuallyReviewedTasks++;
            return;
        }

        deliverNotification(task, vacancyId, analysis, acc);
    }

    private boolean isNowManuallyReviewed(UUID vacancyId) {
        Boolean reviewed = newTransaction.execute(status -> jobAnalysisRepository.findByVacancyId(vacancyId)
                .map(e -> e.getManuallyReviewedAt() != null || e.getAnalysisOrigin() == AnalysisOrigin.MANUAL)
                .orElse(false));
        return Boolean.TRUE.equals(reviewed);
    }

    // --- Notification ------------------------------------------------------------------------

    private void deliverNotification(VacancyRecommendationTaskEntity task, UUID vacancyId, JobAnalysis analysis, RunAccumulator acc) {
        Long recipientChatId = properties.recipientChatId();
        Vacancy vacancy = loadVacancyOrThrow(vacancyId);
        CompactVacancyRecommendation recommendation = jobNotificationFactory.createCompactRecommendation(vacancy, analysis, recipientChatId);

        DeliveryDecision decision = reserveOrRetrieveDelivery(vacancyId, recipientChatId);
        if (decision.sent()) {
            completeTask(task, VacancyRecommendationTaskOutcome.ALREADY_NOTIFIED, acc);
            acc.alreadyNotifiedTasks++;
            return;
        }
        if (decision.delivery() == null) {
            handleRecoverableFailure(task, VacancyRecommendationFailureCategory.DATABASE_CONFLICT, acc);
            return;
        }

        NotificationDelivery delivery = decision.delivery();
        acc.notificationAttempts++;
        JobNotificationResult sendResult;
        try {
            sendResult = jobNotificationPort.sendCompactRecommendation(recommendation);
        } catch (JobNotificationException e) {
            acc.notificationFailures++;
            VacancyRecommendationFailureCategory category = classifyTelegramFailure(e.failureType());
            acc.addIssue(VacancyRecommendationIssueCategory.NOTIFICATION_FAILED, task.getId(), vacancyId, category.name());
            markDeliveryFailedSafely(delivery.id(), e.failureType().name());
            if (category == VacancyRecommendationFailureCategory.TELEGRAM_PERMANENT_ERROR
                    || category == VacancyRecommendationFailureCategory.TELEGRAM_MESSAGE_TOO_LARGE) {
                handleNonRecoverableFailure(task, category, acc);
            } else {
                handleRecoverableFailure(task, category, acc);
            }
            return;
        } catch (RuntimeException e) {
            acc.notificationFailures++;
            acc.addIssue(VacancyRecommendationIssueCategory.NOTIFICATION_FAILED, task.getId(), vacancyId,
                    VacancyRecommendationFailureCategory.INTERNAL_ERROR.name());
            markDeliveryFailedSafely(delivery.id(), JobNotificationFailureType.UNEXPECTED_FAILURE.name());
            handleRecoverableFailure(task, VacancyRecommendationFailureCategory.INTERNAL_ERROR, acc);
            return;
        }

        log.debug("Notification sent for vacancy {} (delivery {}), externalMessageId={}",
                vacancyId, delivery.id(), sendResult.externalMessageId().orElse("n/a"));

        if (markDeliverySentSafely(delivery.id())) {
            completeTask(task, VacancyRecommendationTaskOutcome.NOTIFIED, acc);
            acc.notifiedTasks++;
        } else {
            log.error("CONSISTENCY: Telegram accepted notification for vacancy {} (delivery {}) but markSent "
                    + "could not be persisted", vacancyId, delivery.id());
            handleRecoverableFailure(task, VacancyRecommendationFailureCategory.DATABASE_CONFLICT, acc);
        }
    }

    /**
     * Reuses the existing {@code NotificationDelivery} state machine entirely: a brand-new
     * reservation proceeds directly; an existing {@code SENT} row means nothing more should be
     * sent; an existing {@code PENDING} row (a previous attempt crashed between reserving and
     * resolving) is retried as-is; an existing {@code FAILED} row is atomically reset to {@code
     * PENDING} (see {@code NotificationDeliveryRepositoryCustom#retryFailed}) before retrying - no
     * second delivery table, no new reservation.
     */
    private DeliveryDecision reserveOrRetrieveDelivery(UUID vacancyId, Long recipientChatId) {
        DeliveryDecision result = newTransaction.execute(status -> {
            Instant now = clock.instant();
            NotificationReservationResult reservation = notificationDeliveryRepository.reserve(vacancyId, recipientChatId, now);
            if (reservation.isReserved()) {
                return DeliveryDecision.pending(reservation.delivery());
            }
            Optional<NotificationDelivery> existing = notificationDeliveryRepository.findExistingDelivery(vacancyId, recipientChatId);
            if (existing.isEmpty()) {
                return DeliveryDecision.unavailable();
            }
            NotificationDelivery deliveryRow = existing.get();
            return switch (deliveryRow.status()) {
                case SENT -> DeliveryDecision.alreadySent();
                case PENDING -> DeliveryDecision.pending(deliveryRow);
                case FAILED -> {
                    NotificationDeliveryTransitionResult retry = notificationDeliveryRepository.retryFailed(deliveryRow.id(), now);
                    yield retry.isUpdated() ? DeliveryDecision.pending(retry.delivery()) : DeliveryDecision.unavailable();
                }
            };
        });
        return result == null ? DeliveryDecision.unavailable() : result;
    }

    private boolean markDeliverySentSafely(UUID deliveryId) {
        try {
            NotificationDeliveryTransitionResult transition =
                    newTransaction.execute(status -> notificationDeliveryRepository.markSent(deliveryId, clock.instant()));
            return transition != null && transition.isUpdated();
        } catch (RuntimeException e) {
            log.error("CONSISTENCY: delivery {} was sent by the provider but markSent threw", deliveryId, e);
            return false;
        }
    }

    private void markDeliveryFailedSafely(UUID deliveryId, String failureCode) {
        try {
            newTransaction.executeWithoutResult(status -> {
                NotificationDeliveryTransitionResult transition =
                        notificationDeliveryRepository.markFailed(deliveryId, clock.instant(), failureCode);
                if (!transition.isUpdated()) {
                    log.error("Failed to persist FAILED state for delivery {}: transition returned {}",
                            deliveryId, transition.status());
                }
            });
        } catch (RuntimeException e) {
            log.error("Failed to persist FAILED state for delivery {}", deliveryId, e);
        }
    }

    // --- Task transitions --------------------------------------------------------------------

    private void completeTask(VacancyRecommendationTaskEntity task, VacancyRecommendationTaskOutcome outcome, RunAccumulator acc) {
        Instant now = clock.instant();
        Boolean completed = newTransaction.execute(status -> taskRepository.completeTask(task.getId(), instanceId, outcome, now));
        if (Boolean.TRUE.equals(completed)) {
            acc.completedTasks++;
        } else {
            log.warn("Lease no longer owned while completing task {} (vacancy {}) with outcome {} - "
                            + "leaving for the next claim cycle",
                    task.getId(), task.getVacancyId(), outcome);
        }
    }

    private void handleRecoverableFailure(VacancyRecommendationTaskEntity task, VacancyRecommendationFailureCategory category, RunAccumulator acc) {
        if (task.getAttemptCount() >= properties.processing().maxAttempts()) {
            handleNonRecoverableFailure(task, category, acc);
            return;
        }
        Duration backoff = computeBackoff(task.getAttemptCount(), properties.processing().retryInitialDelay(),
                properties.processing().retryMaxDelay());
        Instant now = clock.instant();
        Instant nextAttemptAt = now.plus(backoff);
        Boolean scheduled = newTransaction.execute(status ->
                taskRepository.scheduleRetry(task.getId(), instanceId, nextAttemptAt, category, now));
        if (Boolean.TRUE.equals(scheduled)) {
            acc.retryScheduled++;
        } else {
            log.warn("Lease no longer owned while scheduling retry for task {} (vacancy {}) - "
                            + "leaving for the next claim cycle",
                    task.getId(), task.getVacancyId());
        }
    }

    private void handleNonRecoverableFailure(VacancyRecommendationTaskEntity task, VacancyRecommendationFailureCategory category, RunAccumulator acc) {
        Instant now = clock.instant();
        Boolean marked = newTransaction.execute(status -> taskRepository.markDead(task.getId(), instanceId, category, now));
        if (Boolean.TRUE.equals(marked)) {
            acc.deadTasks++;
            acc.addIssue(VacancyRecommendationIssueCategory.TASK_DEAD, task.getId(), task.getVacancyId(), category.name());
        } else {
            log.warn("Lease no longer owned while marking task {} (vacancy {}) DEAD - leaving for the next claim cycle",
                    task.getId(), task.getVacancyId());
        }
    }

    private Vacancy loadVacancyOrThrow(UUID vacancyId) {
        return vacancyRepository.findByIdWithCompany(vacancyId)
                .orElseThrow(() -> new IllegalStateException(
                        "Vacancy " + vacancyId + " referenced by a recommendation task no longer exists"));
    }

    // --- Failure classification ----------------------------------------------------------------

    private VacancyRecommendationFailureCategory classifyAnalysisFailure(JobAnalysisException e) {
        if (containsTimeoutCause(e)) {
            return VacancyRecommendationFailureCategory.ANALYSIS_TIMEOUT;
        }
        if (containsRateLimitCause(e)) {
            return VacancyRecommendationFailureCategory.ANALYSIS_RATE_LIMIT;
        }
        if (e.getMessage() != null && e.getMessage().contains("no job analysis")) {
            return VacancyRecommendationFailureCategory.ANALYSIS_INVALID_RESPONSE;
        }
        return VacancyRecommendationFailureCategory.ANALYSIS_PROVIDER_ERROR;
    }

    private boolean containsTimeoutCause(Throwable throwable) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 10) {
            if (current instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            current = current.getCause();
            depth++;
        }
        return false;
    }

    private boolean containsRateLimitCause(Throwable throwable) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 10) {
            String simpleName = current.getClass().getSimpleName();
            if (simpleName.contains("TooManyRequests") || simpleName.contains("RateLimit")) {
                return true;
            }
            current = current.getCause();
            depth++;
        }
        return false;
    }

    private VacancyRecommendationFailureCategory classifyTelegramFailure(JobNotificationFailureType failureType) {
        return switch (failureType) {
            case PERMANENT_FAILURE -> VacancyRecommendationFailureCategory.TELEGRAM_PERMANENT_ERROR;
            case TEMPORARY_FAILURE, UNEXPECTED_FAILURE -> VacancyRecommendationFailureCategory.TELEGRAM_TRANSIENT_ERROR;
            case PAYLOAD_TOO_LARGE -> VacancyRecommendationFailureCategory.TELEGRAM_MESSAGE_TOO_LARGE;
        };
    }

    /**
     * Deterministic bounded exponential backoff: {@code min(retryInitialDelay * 2^(attemptCount-1),
     * retryMaxDelay)}, using overflow-safe {@link Duration#multipliedBy(long)} (which itself throws
     * {@link ArithmeticException} on overflow, mapped here to the configured maximum rather than a
     * wrapped negative duration). No jitter - this project has no existing retry convention that
     * uses it, so none is introduced here.
     */
    static Duration computeBackoff(int attemptCount, Duration retryInitialDelay, Duration retryMaxDelay) {
        if (attemptCount <= 1) {
            return retryInitialDelay.compareTo(retryMaxDelay) > 0 ? retryMaxDelay : retryInitialDelay;
        }
        int shift = Math.min(attemptCount - 1, 40);
        long multiplier = 1L << shift;
        Duration candidate;
        try {
            candidate = retryInitialDelay.multipliedBy(multiplier);
        } catch (ArithmeticException e) {
            return retryMaxDelay;
        }
        return candidate.compareTo(retryMaxDelay) > 0 ? retryMaxDelay : candidate;
    }

    private void logSummary(VacancyRecommendationProcessingResult result) {
        log.info("Vacancy recommendation processing run summary - duration: {}, claimedTasks: {}, completedTasks: {}, "
                        + "notifiedTasks: {}, belowThresholdTasks: {}, manuallyReviewedTasks: {}, alreadyNotifiedTasks: {}, "
                        + "analysisAttempts: {}, analysisReused: {}, analysisFailures: {}, notificationAttempts: {}, "
                        + "notificationFailures: {}, retryScheduled: {}, deadTasks: {}, leaseRecoveredTasks: {}, "
                        + "omittedIssueCount: {}",
                result.duration(), result.claimedTasks(), result.completedTasks(), result.notifiedTasks(),
                result.belowThresholdTasks(), result.manuallyReviewedTasks(), result.alreadyNotifiedTasks(),
                result.analysisAttempts(), result.analysisReused(), result.analysisFailures(), result.notificationAttempts(),
                result.notificationFailures(), result.retryScheduled(), result.deadTasks(), result.leaseRecoveredTasks(),
                result.omittedIssueCount());
    }

    // --- Fixtures ------------------------------------------------------------------------------

    private record ClaimBatch(List<VacancyRecommendationTaskEntity> claimed, int leaseRecoveredCount) {
    }

    private sealed interface AnalysisAttempt {
        record Fresh(JobAnalysis analysis) implements AnalysisAttempt {
        }

        record ExistingWinner(PersistedJobAnalysis analysis) implements AnalysisAttempt {
        }

        record StillInProgress() implements AnalysisAttempt {
        }

        record Failed(VacancyRecommendationFailureCategory category) implements AnalysisAttempt {
        }
    }

    private record DeliveryDecision(boolean sent, NotificationDelivery delivery) {
        static DeliveryDecision alreadySent() {
            return new DeliveryDecision(true, null);
        }

        static DeliveryDecision pending(NotificationDelivery delivery) {
            return new DeliveryDecision(false, delivery);
        }

        static DeliveryDecision unavailable() {
            return new DeliveryDecision(false, null);
        }
    }

    /** Mutable, run-scoped counters/state - never exposed outside this class. */
    private static final class RunAccumulator {

        private static final int MAX_REPORTED_ISSUES = 50;

        private final List<VacancyRecommendationIssue> issues = new ArrayList<>();
        private int omittedIssueCount = 0;

        private int claimedTasks = 0;
        private int completedTasks = 0;
        private int notifiedTasks = 0;
        private int belowThresholdTasks = 0;
        private int manuallyReviewedTasks = 0;
        private int alreadyNotifiedTasks = 0;
        private int analysisAttempts = 0;
        private int analysisReused = 0;
        private int analysisFailures = 0;
        private int notificationAttempts = 0;
        private int notificationFailures = 0;
        private int retryScheduled = 0;
        private int deadTasks = 0;
        private int leaseRecoveredTasks = 0;

        private void addIssue(VacancyRecommendationIssueCategory category, UUID taskId, UUID vacancyId, String sanitizedErrorCategory) {
            if (issues.size() < MAX_REPORTED_ISSUES) {
                issues.add(new VacancyRecommendationIssue(category, taskId, vacancyId, sanitizedErrorCategory));
            } else {
                omittedIssueCount++;
            }
        }

        private VacancyRecommendationProcessingResult toResult(Instant startedAt, Instant completedAt, Duration duration) {
            return new VacancyRecommendationProcessingResult(
                    startedAt, completedAt, duration,
                    claimedTasks, completedTasks, notifiedTasks, belowThresholdTasks, manuallyReviewedTasks,
                    alreadyNotifiedTasks, analysisAttempts, analysisReused, analysisFailures,
                    notificationAttempts, notificationFailures, retryScheduled, deadTasks, leaseRecoveredTasks,
                    issues, omittedIssueCount);
        }
    }
}
