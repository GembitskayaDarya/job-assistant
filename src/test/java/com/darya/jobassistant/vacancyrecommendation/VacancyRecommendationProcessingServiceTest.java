package com.darya.jobassistant.vacancyrecommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.ai.entity.AnalysisStatus;
import com.darya.jobassistant.ai.entity.JobAnalysisEntity;
import com.darya.jobassistant.ai.exception.JobAnalysisException;
import com.darya.jobassistant.ai.model.AnalysisOrigin;
import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.ai.repository.JobAnalysisRepository;
import com.darya.jobassistant.candidatecontext.CandidateContextProvider;
import com.darya.jobassistant.candidatecontext.CandidateContextSnapshot;
import com.darya.jobassistant.candidatecontext.CareerHistoryAvailability;
import com.darya.jobassistant.candidatecontext.analysis.CandidateContextForAnalysis;
import com.darya.jobassistant.candidatecontext.analysis.CandidateContextForAnalysisSelector;
import com.darya.jobassistant.candidatecontext.analysis.CandidateContextSelectionMetadata;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.companies.entity.Company;
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
import com.darya.jobassistant.notifications.entity.NotificationDeliveryStatus;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

/**
 * Mockito-based unit tests for {@link VacancyRecommendationProcessingService}: every branch of
 * the manual-review boundary checks, analysis reuse/race handling, score policy, notification
 * reserve/send/retry, failure classification, and DEAD/max-attempts behavior - all with real
 * {@code TransactionTemplate} instances (only {@link PlatformTransactionManager} is mocked, the
 * same convention as {@code AnalyzeVacancyServiceTest}/{@code VacancyIngestionServiceTest}); real
 * PostgreSQL claim/lease behavior is proven separately by {@code
 * VacancyRecommendationTaskRepositoryConcurrencyTest}.
 */
@ExtendWith(MockitoExtension.class)
class VacancyRecommendationProcessingServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Long RECIPIENT_CHAT_ID = 555L;
    private static final int MINIMUM_SCORE = 70;

    @Mock
    private VacancyRecommendationTaskRepository taskRepository;

    @Mock
    private VacancyRepository vacancyRepository;

    @Mock
    private JobAnalysisRepository jobAnalysisRepository;

    @Mock
    private VacancyJobOfferMapper vacancyJobOfferMapper;

    @Mock
    private CandidateContextProvider candidateContextProvider;

    @Mock
    private CandidateContextForAnalysisSelector candidateContextForAnalysisSelector;

    @Mock
    private JobAnalysisService jobAnalysisService;

    @Mock
    private JobNotificationFactory jobNotificationFactory;

    @Mock
    private JobNotificationPort jobNotificationPort;

    @Mock
    private NotificationDeliveryRepository notificationDeliveryRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private final CandidateContextForAnalysis analysisContext = new CandidateContextForAnalysis(
            mock(CandidateProfile.class), CareerHistoryAvailability.NOT_PROVIDED, List.of(),
            CandidateContextSelectionMetadata.empty(CareerHistoryAvailability.NOT_PROVIDED, null));

    private VacancyRecommendationProcessingService service;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        lenient().when(candidateContextForAnalysisSelector.select(any(), any())).thenReturn(analysisContext);
        RecommendationPolicyProperties policyProperties = new RecommendationPolicyProperties(MINIMUM_SCORE);
        VacancyRecommendationProperties properties = new VacancyRecommendationProperties(
                true, RECIPIENT_CHAT_ID,
                new VacancyRecommendationProperties.Processing(
                        5, 3, Duration.ofMinutes(20), Duration.ofMinutes(10), Duration.ofHours(2)),
                new VacancyRecommendationProperties.Scheduler(
                        false, Duration.ofMinutes(10), Duration.ofMinutes(1), Duration.ofHours(1), Duration.ofSeconds(10)));
        service = new VacancyRecommendationProcessingService(
                taskRepository, vacancyRepository, jobAnalysisRepository, vacancyJobOfferMapper,
                candidateContextProvider, candidateContextForAnalysisSelector, jobAnalysisService,
                jobNotificationFactory, jobNotificationPort, notificationDeliveryRepository, policyProperties,
                properties, CLOCK, transactionManager);
    }

    // --- Claim / batch shape -------------------------------------------------------------------

    @Test
    void processPending_noEligibleTasks_returnsEmptyResultWithoutProcessingAnything() {
        when(taskRepository.selectClaimCandidates(5)).thenReturn(List.of());

        VacancyRecommendationProcessingResult result = service.processPending();

        assertThat(result.claimedTasks()).isZero();
        assertThat(result.completedTasks()).isZero();
        verify(taskRepository, never()).claimByIds(any(), anyLong(), any());
        verify(jobAnalysisRepository, never()).findByVacancyId(any());
    }

    @Test
    void processPending_preClaimIncludesExpiredLeaseProcessingTask_countsAsLeaseRecovered() {
        VacancyRecommendationTaskEntity preClaimProcessing = task(VacancyRecommendationTaskStatus.PROCESSING, 1);
        when(taskRepository.selectClaimCandidates(5)).thenReturn(List.of(preClaimProcessing));
        when(taskRepository.claimByIds(any(), anyLong(), any())).thenReturn(List.of(preClaimProcessing));
        // Vacancy has a manually reviewed analysis already - shortest path to a terminal outcome.
        when(jobAnalysisRepository.findByVacancyId(preClaimProcessing.getVacancyId()))
                .thenReturn(Optional.of(manuallyReviewedEntity()));
        when(taskRepository.completeTask(any(), any(), any(), any())).thenReturn(true);

        VacancyRecommendationProcessingResult result = service.processPending();

        assertThat(result.claimedTasks()).isEqualTo(1);
        assertThat(result.leaseRecoveredTasks()).isEqualTo(1);
    }

    @Test
    void processPending_oneTaskThrowsUnexpectedException_otherTaskStillProcessed() {
        VacancyRecommendationTaskEntity failing = task(VacancyRecommendationTaskStatus.PENDING, 0);
        VacancyRecommendationTaskEntity succeeding = task(VacancyRecommendationTaskStatus.PENDING, 0);
        when(taskRepository.selectClaimCandidates(5)).thenReturn(List.of(failing, succeeding));
        when(taskRepository.claimByIds(any(), anyLong(), any())).thenReturn(List.of(failing, succeeding));
        when(jobAnalysisRepository.findByVacancyId(failing.getVacancyId()))
                .thenThrow(new RuntimeException("boom"));
        when(jobAnalysisRepository.findByVacancyId(succeeding.getVacancyId()))
                .thenReturn(Optional.of(manuallyReviewedEntity()));
        when(taskRepository.completeTask(any(), any(), any(), any())).thenReturn(true);
        when(taskRepository.scheduleRetry(any(), any(), any(), any(), any())).thenReturn(true);

        VacancyRecommendationProcessingResult result = service.processPending();

        assertThat(result.claimedTasks()).isEqualTo(2);
        assertThat(result.completedTasks()).isEqualTo(1); // the manually-reviewed one completed
        assertThat(result.retryScheduled()).isEqualTo(1); // the throwing one got a recoverable retry
        verify(taskRepository).scheduleRetry(eq(failing.getId()), any(), any(), eq(VacancyRecommendationFailureCategory.INTERNAL_ERROR), any());
    }

    // --- Boundary 1: existing analysis ---------------------------------------------------------

    @Test
    void existingAnalysis_manuallyReviewed_suppressesWithoutAiOrNotification() {
        VacancyRecommendationTaskEntity claimedTask = givenSingleClaimedTask();
        when(jobAnalysisRepository.findByVacancyId(claimedTask.getVacancyId()))
                .thenReturn(Optional.of(manuallyReviewedEntity()));
        when(taskRepository.completeTask(eq(claimedTask.getId()), any(), eq(VacancyRecommendationTaskOutcome.MANUALLY_REVIEWED), any()))
                .thenReturn(true);

        VacancyRecommendationProcessingResult result = service.processPending();

        assertThat(result.manuallyReviewedTasks()).isEqualTo(1);
        verify(jobAnalysisService, never()).analyze(any(), any());
        verify(jobNotificationPort, never()).sendCompactRecommendation(any());
    }

    @Test
    void existingAnalysis_manualOriginWithoutTimestamp_stillSuppressed() {
        VacancyRecommendationTaskEntity claimedTask = givenSingleClaimedTask();
        JobAnalysisEntity manualOrigin = analysisEntity(claimedTask.getVacancyId(), AnalysisStatus.COMPLETED, AnalysisOrigin.MANUAL, null, 80);
        when(jobAnalysisRepository.findByVacancyId(claimedTask.getVacancyId())).thenReturn(Optional.of(manualOrigin));
        when(taskRepository.completeTask(eq(claimedTask.getId()), any(), eq(VacancyRecommendationTaskOutcome.MANUALLY_REVIEWED), any()))
                .thenReturn(true);

        VacancyRecommendationProcessingResult result = service.processPending();

        assertThat(result.manuallyReviewedTasks()).isEqualTo(1);
        verify(jobNotificationPort, never()).sendCompactRecommendation(any());
    }

    @Test
    void existingAnalysis_legacyOrigin_completesAsNonAutomaticWithoutNotifying() {
        VacancyRecommendationTaskEntity claimedTask = givenSingleClaimedTask();
        JobAnalysisEntity legacy = analysisEntity(claimedTask.getVacancyId(), AnalysisStatus.COMPLETED, AnalysisOrigin.LEGACY, null, 90);
        when(jobAnalysisRepository.findByVacancyId(claimedTask.getVacancyId())).thenReturn(Optional.of(legacy));
        when(taskRepository.completeTask(
                eq(claimedTask.getId()), any(), eq(VacancyRecommendationTaskOutcome.ANALYSIS_ALREADY_EXISTS_NON_AUTOMATIC), any()))
                .thenReturn(true);

        VacancyRecommendationProcessingResult result = service.processPending();

        assertThat(result.completedTasks()).isEqualTo(1);
        verify(jobNotificationPort, never()).sendCompactRecommendation(any());
        verify(jobAnalysisService, never()).analyze(any(), any());
    }

    @Test
    void existingAnalysis_monitoringOrigin_completesAsNonAutomaticWithoutNotifying() {
        VacancyRecommendationTaskEntity claimedTask = givenSingleClaimedTask();
        JobAnalysisEntity monitoring = analysisEntity(claimedTask.getVacancyId(), AnalysisStatus.COMPLETED, AnalysisOrigin.MONITORING, null, 90);
        when(jobAnalysisRepository.findByVacancyId(claimedTask.getVacancyId())).thenReturn(Optional.of(monitoring));
        when(taskRepository.completeTask(
                eq(claimedTask.getId()), any(), eq(VacancyRecommendationTaskOutcome.ANALYSIS_ALREADY_EXISTS_NON_AUTOMATIC), any()))
                .thenReturn(true);

        service.processPending();

        verify(jobNotificationPort, never()).sendCompactRecommendation(any());
    }

    @Test
    void existingAnalysis_automaticDiscoveryOrigin_reusedWithoutAiCall() {
        VacancyRecommendationTaskEntity claimedTask = givenSingleClaimedTask();
        JobAnalysisEntity automatic = analysisEntity(claimedTask.getVacancyId(), AnalysisStatus.COMPLETED, AnalysisOrigin.AUTOMATIC_DISCOVERY, null, 90);
        when(jobAnalysisRepository.findByVacancyId(claimedTask.getVacancyId())).thenReturn(Optional.of(automatic));
        givenSuccessfulNotificationDelivery(claimedTask.getVacancyId());
        when(taskRepository.completeTask(eq(claimedTask.getId()), any(), eq(VacancyRecommendationTaskOutcome.NOTIFIED), any()))
                .thenReturn(true);

        VacancyRecommendationProcessingResult result = service.processPending();

        assertThat(result.analysisReused()).isEqualTo(1);
        assertThat(result.notifiedTasks()).isEqualTo(1);
        verify(jobAnalysisService, never()).analyze(any(), any());
    }

    @Test
    void existingAnalysis_inProgress_deferredAsRecoverableConflict() {
        VacancyRecommendationTaskEntity claimedTask = givenSingleClaimedTask();
        JobAnalysisEntity inProgress = JobAnalysisEntity.builder()
                .id(UUID.randomUUID())
                .vacancyId(claimedTask.getVacancyId())
                .status(AnalysisStatus.IN_PROGRESS)
                .pros(List.of()).cons(List.of()).missingRequiredSkills(List.of()).missingPreferredSkills(List.of())
                .build();
        when(jobAnalysisRepository.findByVacancyId(claimedTask.getVacancyId())).thenReturn(Optional.of(inProgress));
        when(taskRepository.scheduleRetry(any(), any(), any(), eq(VacancyRecommendationFailureCategory.DATABASE_CONFLICT), any()))
                .thenReturn(true);

        VacancyRecommendationProcessingResult result = service.processPending();

        assertThat(result.retryScheduled()).isEqualTo(1);
        verify(jobAnalysisService, never()).analyze(any(), any());
    }

    // --- No existing analysis: fresh AI call ------------------------------------------------

    @Test
    void noExistingAnalysis_freshAiCall_aboveThreshold_notifiesSuccessfully() {
        VacancyRecommendationTaskEntity claimedTask = givenSingleClaimedTask();
        UUID vacancyId = claimedTask.getVacancyId();
        when(jobAnalysisRepository.findByVacancyId(vacancyId)).thenReturn(Optional.empty());
        JobAnalysisEntity claimEntity = inProgressClaimEntity(vacancyId);
        when(jobAnalysisRepository.claimIfAbsent(eq(vacancyId), any(), eq(AnalysisOrigin.AUTOMATIC_DISCOVERY), eq(null)))
                .thenReturn(Optional.of(claimEntity));
        JobOffer jobOffer = jobOffer();
        when(vacancyJobOfferMapper.toJobOffer(any(Vacancy.class))).thenReturn(jobOffer);
        CandidateProfile profile = mock(CandidateProfile.class);
        CandidateContextSnapshot candidateContext = candidateContextSnapshot(profile);
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        JobAnalysis analysis = analysis(85);
        when(jobAnalysisService.analyze(analysisContext, jobOffer)).thenReturn(analysis);
        when(jobAnalysisRepository.completeClaim(vacancyId, analysis, NOW)).thenReturn(true);
        givenSuccessfulNotificationDelivery(vacancyId);
        when(taskRepository.completeTask(eq(claimedTask.getId()), any(), eq(VacancyRecommendationTaskOutcome.NOTIFIED), any()))
                .thenReturn(true);

        VacancyRecommendationProcessingResult result = service.processPending();

        assertThat(result.analysisAttempts()).isEqualTo(1);
        assertThat(result.notifiedTasks()).isEqualTo(1);
        verify(jobAnalysisService, times(1)).analyze(analysisContext, jobOffer);
    }

    @Test
    void noExistingAnalysis_belowThreshold_completesWithoutNotifying() {
        VacancyRecommendationTaskEntity claimedTask = givenSingleClaimedTask();
        UUID vacancyId = claimedTask.getVacancyId();
        when(jobAnalysisRepository.findByVacancyId(vacancyId)).thenReturn(Optional.empty());
        when(jobAnalysisRepository.claimIfAbsent(eq(vacancyId), any(), eq(AnalysisOrigin.AUTOMATIC_DISCOVERY), eq(null)))
                .thenReturn(Optional.of(inProgressClaimEntity(vacancyId)));
        Vacancy vacancy = vacancy(vacancyId);
        when(vacancyRepository.findByIdWithCompany(vacancyId)).thenReturn(Optional.of(vacancy));
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(jobOffer());
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContextSnapshot(mock(CandidateProfile.class)));
        JobAnalysis analysis = analysis(40); // below MINIMUM_SCORE (70)
        when(jobAnalysisService.analyze(any(), any())).thenReturn(analysis);
        when(jobAnalysisRepository.completeClaim(vacancyId, analysis, NOW)).thenReturn(true);
        when(taskRepository.completeTask(eq(claimedTask.getId()), any(), eq(VacancyRecommendationTaskOutcome.BELOW_SCORE_THRESHOLD), any()))
                .thenReturn(true);

        VacancyRecommendationProcessingResult result = service.processPending();

        assertThat(result.belowThresholdTasks()).isEqualTo(1);
        verify(jobNotificationPort, never()).sendCompactRecommendation(any());
    }

    @Test
    void noExistingAnalysis_manuallyReviewedBetweenAnalysisAndScoreCheck_suppressesAtBoundaryTwo() {
        VacancyRecommendationTaskEntity claimedTask = givenSingleClaimedTask();
        UUID vacancyId = claimedTask.getVacancyId();
        when(jobAnalysisRepository.findByVacancyId(vacancyId)).thenReturn(Optional.empty());
        when(jobAnalysisRepository.claimIfAbsent(eq(vacancyId), any(), eq(AnalysisOrigin.AUTOMATIC_DISCOVERY), eq(null)))
                .thenReturn(Optional.of(inProgressClaimEntity(vacancyId)));
        Vacancy vacancy = vacancy(vacancyId);
        when(vacancyRepository.findByIdWithCompany(vacancyId)).thenReturn(Optional.of(vacancy));
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(jobOffer());
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContextSnapshot(mock(CandidateProfile.class)));
        JobAnalysis analysis = analysis(90);
        when(jobAnalysisService.analyze(any(), any())).thenReturn(analysis);
        when(jobAnalysisRepository.completeClaim(vacancyId, analysis, NOW)).thenReturn(true);
        // Boundary-2/3 recheck: a manual /analyze committed a review in the meantime.
        when(jobAnalysisRepository.findByVacancyId(vacancyId))
                .thenReturn(Optional.empty(), Optional.of(manuallyReviewedEntity(vacancyId)));
        when(taskRepository.completeTask(eq(claimedTask.getId()), any(), eq(VacancyRecommendationTaskOutcome.MANUALLY_REVIEWED), any()))
                .thenReturn(true);

        VacancyRecommendationProcessingResult result = service.processPending();

        assertThat(result.manuallyReviewedTasks()).isEqualTo(1);
        verify(jobNotificationPort, never()).sendCompactRecommendation(any());
    }

    @Test
    void noExistingAnalysis_claimLost_reloadFindsCompletedWinner_reusesWithoutSecondAiCall() {
        VacancyRecommendationTaskEntity claimedTask = givenSingleClaimedTask();
        UUID vacancyId = claimedTask.getVacancyId();
        when(jobAnalysisRepository.findByVacancyId(vacancyId)).thenReturn(Optional.empty());
        when(jobAnalysisRepository.claimIfAbsent(eq(vacancyId), any(), eq(AnalysisOrigin.AUTOMATIC_DISCOVERY), eq(null)))
                .thenReturn(Optional.empty()); // lost the claim race to a concurrent /analyze
        JobAnalysisEntity winner = analysisEntity(vacancyId, AnalysisStatus.COMPLETED, AnalysisOrigin.MANUAL, NOW, 95);
        when(jobAnalysisRepository.findByVacancyId(vacancyId)).thenReturn(Optional.empty(), Optional.of(winner));
        when(taskRepository.completeTask(eq(claimedTask.getId()), any(), eq(VacancyRecommendationTaskOutcome.MANUALLY_REVIEWED), any()))
                .thenReturn(true);

        VacancyRecommendationProcessingResult result = service.processPending();

        assertThat(result.manuallyReviewedTasks()).isEqualTo(1);
        verify(jobAnalysisService, never()).analyze(any(), any());
        verify(vacancyRepository, never()).findByIdWithCompany(any());
    }

    @Test
    void noExistingAnalysis_claimLost_reloadFindsStillInProgress_schedulesRetry() {
        VacancyRecommendationTaskEntity claimedTask = givenSingleClaimedTask();
        UUID vacancyId = claimedTask.getVacancyId();
        when(jobAnalysisRepository.claimIfAbsent(eq(vacancyId), any(), eq(AnalysisOrigin.AUTOMATIC_DISCOVERY), eq(null)))
                .thenReturn(Optional.empty());
        JobAnalysisEntity stillInProgress = JobAnalysisEntity.builder()
                .id(UUID.randomUUID()).vacancyId(vacancyId).status(AnalysisStatus.IN_PROGRESS)
                .pros(List.of()).cons(List.of()).missingRequiredSkills(List.of()).missingPreferredSkills(List.of())
                .build();
        when(jobAnalysisRepository.findByVacancyId(vacancyId)).thenReturn(Optional.empty(), Optional.of(stillInProgress));
        when(taskRepository.scheduleRetry(any(), any(), any(), eq(VacancyRecommendationFailureCategory.DATABASE_CONFLICT), any()))
                .thenReturn(true);

        VacancyRecommendationProcessingResult result = service.processPending();

        assertThat(result.retryScheduled()).isEqualTo(1);
    }

    @Test
    void noExistingAnalysis_claimLost_reloadFindsNothing_recoverableInternalError() {
        VacancyRecommendationTaskEntity claimedTask = givenSingleClaimedTask();
        UUID vacancyId = claimedTask.getVacancyId();
        when(jobAnalysisRepository.claimIfAbsent(eq(vacancyId), any(), eq(AnalysisOrigin.AUTOMATIC_DISCOVERY), eq(null)))
                .thenReturn(Optional.empty());
        when(jobAnalysisRepository.findByVacancyId(vacancyId)).thenReturn(Optional.empty(), Optional.empty());
        when(taskRepository.scheduleRetry(any(), any(), any(), eq(VacancyRecommendationFailureCategory.INTERNAL_ERROR), any()))
                .thenReturn(true);

        VacancyRecommendationProcessingResult result = service.processPending();

        assertThat(result.retryScheduled()).isEqualTo(1);
    }

    // --- AI failure classification ------------------------------------------------------------

    @Test
    void aiCallFails_timeoutCause_classifiedAsAnalysisTimeout_releasesClaimAndSchedulesRetry() {
        VacancyRecommendationTaskEntity claimedTask = givenSingleClaimedTask();
        UUID vacancyId = claimedTask.getVacancyId();
        setUpFreshAiCallPreamble(vacancyId);
        when(jobAnalysisService.analyze(any(), any()))
                .thenThrow(new JobAnalysisException("provider call failed", new RuntimeException(new TimeoutException("slow"))));
        when(taskRepository.scheduleRetry(any(), any(), any(), eq(VacancyRecommendationFailureCategory.ANALYSIS_TIMEOUT), any()))
                .thenReturn(true);

        VacancyRecommendationProcessingResult result = service.processPending();

        assertThat(result.analysisFailures()).isEqualTo(1);
        assertThat(result.retryScheduled()).isEqualTo(1);
        verify(jobAnalysisRepository).releaseClaim(vacancyId);
    }

    @Test
    void aiCallFails_rateLimitCause_classifiedAsAnalysisRateLimit_recoverable() {
        VacancyRecommendationTaskEntity claimedTask = givenSingleClaimedTask();
        UUID vacancyId = claimedTask.getVacancyId();
        setUpFreshAiCallPreamble(vacancyId);
        RuntimeException rateLimitCause = new RuntimeException("429") {
            @Override
            public StackTraceElement[] getStackTrace() {
                return new StackTraceElement[0];
            }
        };
        class TooManyRequestsException extends RuntimeException {
            TooManyRequestsException() {
                super("too many requests");
            }
        }
        when(jobAnalysisService.analyze(any(), any()))
                .thenThrow(new JobAnalysisException("provider call failed", new TooManyRequestsException()));
        when(taskRepository.scheduleRetry(any(), any(), any(), eq(VacancyRecommendationFailureCategory.ANALYSIS_RATE_LIMIT), any()))
                .thenReturn(true);

        VacancyRecommendationProcessingResult result = service.processPending();

        assertThat(result.retryScheduled()).isEqualTo(1);
        verify(jobAnalysisRepository).releaseClaim(vacancyId);
    }

    @Test
    void aiCallFails_invalidResponseMessage_isNonRecoverable_marksTaskDeadOnFirstAttempt() {
        VacancyRecommendationTaskEntity claimedTask = task(VacancyRecommendationTaskStatus.PENDING, 0);
        UUID vacancyId = claimedTask.getVacancyId();
        when(taskRepository.selectClaimCandidates(5)).thenReturn(List.of(claimedTask));
        when(taskRepository.claimByIds(any(), anyLong(), any())).thenReturn(List.of(claimedTask));
        setUpFreshAiCallPreamble(vacancyId);
        when(jobAnalysisService.analyze(any(), any()))
                .thenThrow(new JobAnalysisException("no job analysis produced by the model"));
        when(taskRepository.markDead(any(), any(), eq(VacancyRecommendationFailureCategory.ANALYSIS_INVALID_RESPONSE), any()))
                .thenReturn(true);

        VacancyRecommendationProcessingResult result = service.processPending();

        assertThat(result.deadTasks()).isEqualTo(1);
        verify(taskRepository, never()).scheduleRetry(any(), any(), any(), any(), any());
    }

    @Test
    void aiCallFails_unclassifiedCause_defaultsToProviderErrorAndIsRecoverable() {
        VacancyRecommendationTaskEntity claimedTask = givenSingleClaimedTask();
        UUID vacancyId = claimedTask.getVacancyId();
        setUpFreshAiCallPreamble(vacancyId);
        when(jobAnalysisService.analyze(any(), any())).thenThrow(new JobAnalysisException("unexpected provider error"));
        when(taskRepository.scheduleRetry(any(), any(), any(), eq(VacancyRecommendationFailureCategory.ANALYSIS_PROVIDER_ERROR), any()))
                .thenReturn(true);

        VacancyRecommendationProcessingResult result = service.processPending();

        assertThat(result.retryScheduled()).isEqualTo(1);
    }

    // --- Notification reservation/delivery ------------------------------------------------------

    @Test
    void notification_existingSentDelivery_completesAsAlreadyNotifiedWithoutSending() {
        VacancyRecommendationTaskEntity claimedTask = givenSingleClaimedTask();
        UUID vacancyId = claimedTask.getVacancyId();
        JobAnalysisEntity automatic = analysisEntity(vacancyId, AnalysisStatus.COMPLETED, AnalysisOrigin.AUTOMATIC_DISCOVERY, null, 90);
        when(jobAnalysisRepository.findByVacancyId(vacancyId)).thenReturn(Optional.of(automatic));
        Vacancy vacancy = vacancy(vacancyId);
        when(vacancyRepository.findByIdWithCompany(vacancyId)).thenReturn(Optional.of(vacancy));
        when(jobNotificationFactory.createCompactRecommendation(eq(vacancy), any(), eq(RECIPIENT_CHAT_ID))).thenReturn(notification(vacancyId));
        when(notificationDeliveryRepository.reserve(eq(vacancyId), eq(RECIPIENT_CHAT_ID), any()))
                .thenReturn(NotificationReservationResult.alreadyExists());
        when(notificationDeliveryRepository.findExistingDelivery(vacancyId, RECIPIENT_CHAT_ID))
                .thenReturn(Optional.of(delivery(vacancyId, NotificationDeliveryStatus.SENT)));
        when(taskRepository.completeTask(eq(claimedTask.getId()), any(), eq(VacancyRecommendationTaskOutcome.ALREADY_NOTIFIED), any()))
                .thenReturn(true);

        VacancyRecommendationProcessingResult result = service.processPending();

        assertThat(result.alreadyNotifiedTasks()).isEqualTo(1);
        verify(jobNotificationPort, never()).sendCompactRecommendation(any());
    }

    @Test
    void notification_reserveFailsAndNoExistingDeliveryFound_recoverableConflict() {
        VacancyRecommendationTaskEntity claimedTask = givenSingleClaimedTask();
        UUID vacancyId = claimedTask.getVacancyId();
        JobAnalysisEntity automatic = analysisEntity(vacancyId, AnalysisStatus.COMPLETED, AnalysisOrigin.AUTOMATIC_DISCOVERY, null, 90);
        when(jobAnalysisRepository.findByVacancyId(vacancyId)).thenReturn(Optional.of(automatic));
        Vacancy vacancy = vacancy(vacancyId);
        when(vacancyRepository.findByIdWithCompany(vacancyId)).thenReturn(Optional.of(vacancy));
        when(jobNotificationFactory.createCompactRecommendation(eq(vacancy), any(), eq(RECIPIENT_CHAT_ID))).thenReturn(notification(vacancyId));
        when(notificationDeliveryRepository.reserve(eq(vacancyId), eq(RECIPIENT_CHAT_ID), any()))
                .thenReturn(NotificationReservationResult.alreadyExists());
        when(notificationDeliveryRepository.findExistingDelivery(vacancyId, RECIPIENT_CHAT_ID)).thenReturn(Optional.empty());
        when(taskRepository.scheduleRetry(any(), any(), any(), eq(VacancyRecommendationFailureCategory.DATABASE_CONFLICT), any()))
                .thenReturn(true);

        VacancyRecommendationProcessingResult result = service.processPending();

        assertThat(result.retryScheduled()).isEqualTo(1);
        verify(jobNotificationPort, never()).sendCompactRecommendation(any());
    }

    @Test
    void notification_existingFailedDelivery_isRetriedAndSentSuccessfully() {
        VacancyRecommendationTaskEntity claimedTask = givenSingleClaimedTask();
        UUID vacancyId = claimedTask.getVacancyId();
        JobAnalysisEntity automatic = analysisEntity(vacancyId, AnalysisStatus.COMPLETED, AnalysisOrigin.AUTOMATIC_DISCOVERY, null, 90);
        when(jobAnalysisRepository.findByVacancyId(vacancyId)).thenReturn(Optional.of(automatic));
        Vacancy vacancy = vacancy(vacancyId);
        when(vacancyRepository.findByIdWithCompany(vacancyId)).thenReturn(Optional.of(vacancy));
        when(jobNotificationFactory.createCompactRecommendation(eq(vacancy), any(), eq(RECIPIENT_CHAT_ID))).thenReturn(notification(vacancyId));
        when(notificationDeliveryRepository.reserve(eq(vacancyId), eq(RECIPIENT_CHAT_ID), any()))
                .thenReturn(NotificationReservationResult.alreadyExists());
        NotificationDelivery failedDelivery = delivery(vacancyId, NotificationDeliveryStatus.FAILED);
        when(notificationDeliveryRepository.findExistingDelivery(vacancyId, RECIPIENT_CHAT_ID)).thenReturn(Optional.of(failedDelivery));
        NotificationDelivery retried = new NotificationDelivery(
                failedDelivery.id(), vacancyId, RECIPIENT_CHAT_ID, NotificationDeliveryStatus.PENDING, NOW, null, null, null);
        when(notificationDeliveryRepository.retryFailed(failedDelivery.id(), NOW))
                .thenReturn(NotificationDeliveryTransitionResult.updated(retried));
        when(jobNotificationPort.sendCompactRecommendation(any())).thenReturn(JobNotificationResult.accepted("msg-1"));
        when(notificationDeliveryRepository.markSent(retried.id(), NOW))
                .thenReturn(NotificationDeliveryTransitionResult.updated(
                        new NotificationDelivery(retried.id(), vacancyId, RECIPIENT_CHAT_ID, NotificationDeliveryStatus.SENT, NOW, NOW, null, null)));
        when(taskRepository.completeTask(eq(claimedTask.getId()), any(), eq(VacancyRecommendationTaskOutcome.NOTIFIED), any()))
                .thenReturn(true);

        VacancyRecommendationProcessingResult result = service.processPending();

        assertThat(result.notifiedTasks()).isEqualTo(1);
        verify(jobNotificationPort, times(1)).sendCompactRecommendation(any());
    }

    @Test
    void notification_sendThrowsPermanentFailure_marksTaskDeadWithoutRetry() {
        VacancyRecommendationTaskEntity claimedTask = givenSingleClaimedTask();
        UUID vacancyId = claimedTask.getVacancyId();
        JobAnalysisEntity automatic = analysisEntity(vacancyId, AnalysisStatus.COMPLETED, AnalysisOrigin.AUTOMATIC_DISCOVERY, null, 90);
        when(jobAnalysisRepository.findByVacancyId(vacancyId)).thenReturn(Optional.of(automatic));
        Vacancy vacancy = vacancy(vacancyId);
        when(vacancyRepository.findByIdWithCompany(vacancyId)).thenReturn(Optional.of(vacancy));
        when(jobNotificationFactory.createCompactRecommendation(eq(vacancy), any(), eq(RECIPIENT_CHAT_ID))).thenReturn(notification(vacancyId));
        NotificationDelivery pendingDelivery = delivery(vacancyId, NotificationDeliveryStatus.PENDING);
        when(notificationDeliveryRepository.reserve(eq(vacancyId), eq(RECIPIENT_CHAT_ID), any()))
                .thenReturn(NotificationReservationResult.reserved(pendingDelivery));
        when(jobNotificationPort.sendCompactRecommendation(any()))
                .thenThrow(new JobNotificationException(JobNotificationFailureType.PERMANENT_FAILURE, "bot blocked by user"));
        when(notificationDeliveryRepository.markFailed(eq(pendingDelivery.id()), any(), any()))
                .thenReturn(NotificationDeliveryTransitionResult.updated(
                        new NotificationDelivery(pendingDelivery.id(), vacancyId, RECIPIENT_CHAT_ID, NotificationDeliveryStatus.FAILED, NOW, null, NOW, "PERMANENT_FAILURE")));
        when(taskRepository.markDead(any(), any(), eq(VacancyRecommendationFailureCategory.TELEGRAM_PERMANENT_ERROR), any()))
                .thenReturn(true);

        VacancyRecommendationProcessingResult result = service.processPending();

        assertThat(result.deadTasks()).isEqualTo(1);
        assertThat(result.notificationFailures()).isEqualTo(1);
        verify(taskRepository, never()).scheduleRetry(any(), any(), any(), any(), any());
        // One processing attempt makes at most one Telegram send request - a permanent failure
        // must never be retried transparently within the same attempt.
        verify(jobNotificationPort, times(1)).sendCompactRecommendation(any());
    }

    @Test
    void notification_sendThrowsTemporaryFailure_isRecoverable() {
        VacancyRecommendationTaskEntity claimedTask = givenSingleClaimedTask();
        UUID vacancyId = claimedTask.getVacancyId();
        JobAnalysisEntity automatic = analysisEntity(vacancyId, AnalysisStatus.COMPLETED, AnalysisOrigin.AUTOMATIC_DISCOVERY, null, 90);
        when(jobAnalysisRepository.findByVacancyId(vacancyId)).thenReturn(Optional.of(automatic));
        Vacancy vacancy = vacancy(vacancyId);
        when(vacancyRepository.findByIdWithCompany(vacancyId)).thenReturn(Optional.of(vacancy));
        when(jobNotificationFactory.createCompactRecommendation(eq(vacancy), any(), eq(RECIPIENT_CHAT_ID))).thenReturn(notification(vacancyId));
        NotificationDelivery pendingDelivery = delivery(vacancyId, NotificationDeliveryStatus.PENDING);
        when(notificationDeliveryRepository.reserve(eq(vacancyId), eq(RECIPIENT_CHAT_ID), any()))
                .thenReturn(NotificationReservationResult.reserved(pendingDelivery));
        when(jobNotificationPort.sendCompactRecommendation(any()))
                .thenThrow(new JobNotificationException(JobNotificationFailureType.TEMPORARY_FAILURE, "telegram unavailable"));
        when(notificationDeliveryRepository.markFailed(eq(pendingDelivery.id()), any(), any()))
                .thenReturn(NotificationDeliveryTransitionResult.updated(
                        new NotificationDelivery(pendingDelivery.id(), vacancyId, RECIPIENT_CHAT_ID, NotificationDeliveryStatus.FAILED, NOW, null, NOW, "TEMPORARY_FAILURE")));
        when(taskRepository.scheduleRetry(any(), any(), any(), eq(VacancyRecommendationFailureCategory.TELEGRAM_TRANSIENT_ERROR), any()))
                .thenReturn(true);

        VacancyRecommendationProcessingResult result = service.processPending();

        assertThat(result.retryScheduled()).isEqualTo(1);
        verify(taskRepository, never()).markDead(any(), any(), any(), any());
        // One processing attempt makes at most one Telegram send request - the durable task retry
        // (a later, separate claim) is the only mechanism that may try again, never a hidden
        // transport-level retry within this same attempt.
        verify(jobNotificationPort, times(1)).sendCompactRecommendation(any());
    }

    @Test
    void notification_sendSucceedsButMarkSentFails_treatedAsRecoverableConflictNotAsSuccess() {
        VacancyRecommendationTaskEntity claimedTask = givenSingleClaimedTask();
        UUID vacancyId = claimedTask.getVacancyId();
        JobAnalysisEntity automatic = analysisEntity(vacancyId, AnalysisStatus.COMPLETED, AnalysisOrigin.AUTOMATIC_DISCOVERY, null, 90);
        when(jobAnalysisRepository.findByVacancyId(vacancyId)).thenReturn(Optional.of(automatic));
        Vacancy vacancy = vacancy(vacancyId);
        when(vacancyRepository.findByIdWithCompany(vacancyId)).thenReturn(Optional.of(vacancy));
        when(jobNotificationFactory.createCompactRecommendation(eq(vacancy), any(), eq(RECIPIENT_CHAT_ID))).thenReturn(notification(vacancyId));
        NotificationDelivery pendingDelivery = delivery(vacancyId, NotificationDeliveryStatus.PENDING);
        when(notificationDeliveryRepository.reserve(eq(vacancyId), eq(RECIPIENT_CHAT_ID), any()))
                .thenReturn(NotificationReservationResult.reserved(pendingDelivery));
        when(jobNotificationPort.sendCompactRecommendation(any())).thenReturn(JobNotificationResult.accepted());
        when(notificationDeliveryRepository.markSent(pendingDelivery.id(), NOW))
                .thenReturn(NotificationDeliveryTransitionResult.invalidState());
        when(taskRepository.scheduleRetry(any(), any(), any(), eq(VacancyRecommendationFailureCategory.DATABASE_CONFLICT), any()))
                .thenReturn(true);

        VacancyRecommendationProcessingResult result = service.processPending();

        assertThat(result.retryScheduled()).isEqualTo(1);
        assertThat(result.notifiedTasks()).isZero(); // never counted as success despite the send succeeding
    }

    @Test
    void boundaryThree_manuallyReviewedImmediatelyBeforeReservation_suppressesWithoutReservingOrSending() {
        VacancyRecommendationTaskEntity claimedTask = givenSingleClaimedTask();
        UUID vacancyId = claimedTask.getVacancyId();
        JobAnalysisEntity automatic = analysisEntity(vacancyId, AnalysisStatus.COMPLETED, AnalysisOrigin.AUTOMATIC_DISCOVERY, null, 90);
        // First read (Boundary 1/2 recheck via handleExistingAnalysis -> proceedAfterAnalysis) sees
        // no manual review yet; the Boundary-3 recheck immediately before reservation sees one that
        // committed in between.
        when(jobAnalysisRepository.findByVacancyId(vacancyId)).thenReturn(
                Optional.of(automatic), Optional.of(manuallyReviewedEntity(vacancyId)));
        when(taskRepository.completeTask(eq(claimedTask.getId()), any(), eq(VacancyRecommendationTaskOutcome.MANUALLY_REVIEWED), any()))
                .thenReturn(true);

        VacancyRecommendationProcessingResult result = service.processPending();

        assertThat(result.manuallyReviewedTasks()).isEqualTo(1);
        verify(notificationDeliveryRepository, never()).reserve(any(), any(), any());
        verify(jobNotificationPort, never()).sendCompactRecommendation(any());
    }

    // --- Retry/DEAD attempt-count boundary ------------------------------------------------------

    @Test
    void recoverableFailure_belowMaxAttempts_schedulesRetryNotDead() {
        VacancyRecommendationTaskEntity claimedTask = task(VacancyRecommendationTaskStatus.PENDING, 2); // maxAttempts = 3
        UUID vacancyId = claimedTask.getVacancyId();
        when(taskRepository.selectClaimCandidates(5)).thenReturn(List.of(claimedTask));
        when(taskRepository.claimByIds(any(), anyLong(), any())).thenReturn(List.of(claimedTask));
        JobAnalysisEntity inProgress = JobAnalysisEntity.builder()
                .id(UUID.randomUUID()).vacancyId(vacancyId).status(AnalysisStatus.IN_PROGRESS)
                .pros(List.of()).cons(List.of()).missingRequiredSkills(List.of()).missingPreferredSkills(List.of())
                .build();
        when(jobAnalysisRepository.findByVacancyId(vacancyId)).thenReturn(Optional.of(inProgress));
        when(taskRepository.scheduleRetry(any(), any(), any(), any(), any())).thenReturn(true);

        VacancyRecommendationProcessingResult result = service.processPending();

        assertThat(result.retryScheduled()).isEqualTo(1);
        verify(taskRepository, never()).markDead(any(), any(), any(), any());
    }

    @Test
    void recoverableFailure_atOrAboveMaxAttempts_marksDeadInsteadOfRetrying() {
        VacancyRecommendationTaskEntity claimedTask = task(VacancyRecommendationTaskStatus.PENDING, 3); // == maxAttempts
        UUID vacancyId = claimedTask.getVacancyId();
        when(taskRepository.selectClaimCandidates(5)).thenReturn(List.of(claimedTask));
        when(taskRepository.claimByIds(any(), anyLong(), any())).thenReturn(List.of(claimedTask));
        JobAnalysisEntity inProgress = JobAnalysisEntity.builder()
                .id(UUID.randomUUID()).vacancyId(vacancyId).status(AnalysisStatus.IN_PROGRESS)
                .pros(List.of()).cons(List.of()).missingRequiredSkills(List.of()).missingPreferredSkills(List.of())
                .build();
        when(jobAnalysisRepository.findByVacancyId(vacancyId)).thenReturn(Optional.of(inProgress));
        when(taskRepository.markDead(any(), any(), eq(VacancyRecommendationFailureCategory.DATABASE_CONFLICT), any()))
                .thenReturn(true);

        VacancyRecommendationProcessingResult result = service.processPending();

        assertThat(result.deadTasks()).isEqualTo(1);
        verify(taskRepository, never()).scheduleRetry(any(), any(), any(), any(), any());
    }

    /**
     * Sprint 8 Step 11A.1 smoke-test configuration proof: with {@code maxAttempts=1} (the intended
     * controlled-live-run setting), a single Telegram temporary failure already exhausts the only
     * allowed attempt - the task goes straight to DEAD, never RETRY_WAIT, so no later application
     * retry (and therefore no second Telegram send) can ever happen for this task.
     */
    @Test
    void maxAttemptsOne_telegramTemporaryFailure_goesStraightToDead_createsNoLaterTaskRetry() {
        VacancyRecommendationProcessingService serviceWithMaxAttemptsOne = serviceWithMaxAttempts(1);
        // attemptCount=1 represents the post-claim state claimByIds would return (production
        // increments attempt_count exactly once per claim) - already == maxAttempts(1).
        VacancyRecommendationTaskEntity claimedTask = task(VacancyRecommendationTaskStatus.PENDING, 1);
        UUID vacancyId = claimedTask.getVacancyId();
        when(taskRepository.selectClaimCandidates(1)).thenReturn(List.of(claimedTask));
        when(taskRepository.claimByIds(any(), anyLong(), any())).thenReturn(List.of(claimedTask));
        JobAnalysisEntity automatic = analysisEntity(vacancyId, AnalysisStatus.COMPLETED, AnalysisOrigin.AUTOMATIC_DISCOVERY, null, 90);
        when(jobAnalysisRepository.findByVacancyId(vacancyId)).thenReturn(Optional.of(automatic));
        Vacancy vacancy = vacancy(vacancyId);
        when(vacancyRepository.findByIdWithCompany(vacancyId)).thenReturn(Optional.of(vacancy));
        when(jobNotificationFactory.createCompactRecommendation(eq(vacancy), any(), eq(RECIPIENT_CHAT_ID))).thenReturn(notification(vacancyId));
        NotificationDelivery pendingDelivery = delivery(vacancyId, NotificationDeliveryStatus.PENDING);
        when(notificationDeliveryRepository.reserve(eq(vacancyId), eq(RECIPIENT_CHAT_ID), any()))
                .thenReturn(NotificationReservationResult.reserved(pendingDelivery));
        when(jobNotificationPort.sendCompactRecommendation(any()))
                .thenThrow(new JobNotificationException(JobNotificationFailureType.TEMPORARY_FAILURE, "telegram unavailable"));
        when(notificationDeliveryRepository.markFailed(eq(pendingDelivery.id()), any(), any()))
                .thenReturn(NotificationDeliveryTransitionResult.updated(
                        new NotificationDelivery(pendingDelivery.id(), vacancyId, RECIPIENT_CHAT_ID, NotificationDeliveryStatus.FAILED, NOW, null, NOW, "TEMPORARY_FAILURE")));
        when(taskRepository.markDead(any(), any(), eq(VacancyRecommendationFailureCategory.TELEGRAM_TRANSIENT_ERROR), any()))
                .thenReturn(true);

        VacancyRecommendationProcessingResult result = serviceWithMaxAttemptsOne.processPending();

        assertThat(result.deadTasks()).isEqualTo(1);
        verify(taskRepository, never()).scheduleRetry(any(), any(), any(), any(), any());
        verify(jobNotificationPort, times(1)).sendCompactRecommendation(any());
    }

    private VacancyRecommendationProcessingService serviceWithMaxAttempts(int maxAttempts) {
        RecommendationPolicyProperties policyProperties = new RecommendationPolicyProperties(MINIMUM_SCORE);
        VacancyRecommendationProperties properties = new VacancyRecommendationProperties(
                true, RECIPIENT_CHAT_ID,
                new VacancyRecommendationProperties.Processing(
                        1, maxAttempts, Duration.ofMinutes(20), Duration.ofMinutes(10), Duration.ofHours(2)),
                new VacancyRecommendationProperties.Scheduler(
                        false, Duration.ofMinutes(10), Duration.ofMinutes(1), Duration.ofHours(1), Duration.ofSeconds(10)));
        return new VacancyRecommendationProcessingService(
                taskRepository, vacancyRepository, jobAnalysisRepository, vacancyJobOfferMapper,
                candidateContextProvider, candidateContextForAnalysisSelector, jobAnalysisService,
                jobNotificationFactory, jobNotificationPort, notificationDeliveryRepository, policyProperties,
                properties, CLOCK, transactionManager);
    }

    @Test
    void completeTask_leaseNoLongerOwned_doesNotCountAsCompletedAndDoesNotThrow() {
        VacancyRecommendationTaskEntity claimedTask = givenSingleClaimedTask();
        when(jobAnalysisRepository.findByVacancyId(claimedTask.getVacancyId()))
                .thenReturn(Optional.of(manuallyReviewedEntity(claimedTask.getVacancyId())));
        when(taskRepository.completeTask(any(), any(), any(), any())).thenReturn(false);

        VacancyRecommendationProcessingResult result = service.processPending();

        assertThat(result.completedTasks()).isZero();
        assertThat(result.manuallyReviewedTasks()).isEqualTo(1); // still counted as the decided outcome
    }

    // --- Backoff (static, pure function) --------------------------------------------------------

    @Test
    void computeBackoff_firstAttempt_returnsInitialDelay() {
        Duration backoff = VacancyRecommendationProcessingService.computeBackoff(1, Duration.ofMinutes(10), Duration.ofHours(2));
        assertThat(backoff).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void computeBackoff_secondAttempt_doublesInitialDelay() {
        Duration backoff = VacancyRecommendationProcessingService.computeBackoff(2, Duration.ofMinutes(10), Duration.ofHours(2));
        assertThat(backoff).isEqualTo(Duration.ofMinutes(20));
    }

    @Test
    void computeBackoff_growsExponentially_untilClampedAtMax() {
        Duration backoff = VacancyRecommendationProcessingService.computeBackoff(5, Duration.ofMinutes(10), Duration.ofHours(2));
        // 10 * 2^4 = 160 minutes, clamped to 120 minutes (2 hours)
        assertThat(backoff).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void computeBackoff_veryLargeAttemptCount_doesNotOverflowAndClampsToMax() {
        Duration backoff = VacancyRecommendationProcessingService.computeBackoff(1000, Duration.ofMinutes(10), Duration.ofHours(2));
        assertThat(backoff).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void computeBackoff_initialDelayAlreadyAboveMax_clampsFirstAttemptToo() {
        Duration backoff = VacancyRecommendationProcessingService.computeBackoff(1, Duration.ofHours(3), Duration.ofHours(2));
        assertThat(backoff).isEqualTo(Duration.ofHours(2));
    }

    // --- Fixtures -------------------------------------------------------------------------------

    private VacancyRecommendationTaskEntity givenSingleClaimedTask() {
        VacancyRecommendationTaskEntity claimedTask = task(VacancyRecommendationTaskStatus.PENDING, 0);
        when(taskRepository.selectClaimCandidates(5)).thenReturn(List.of(claimedTask));
        when(taskRepository.claimByIds(any(), anyLong(), any())).thenReturn(List.of(claimedTask));
        return claimedTask;
    }

    private void setUpFreshAiCallPreamble(UUID vacancyId) {
        when(jobAnalysisRepository.findByVacancyId(vacancyId)).thenReturn(Optional.empty());
        when(jobAnalysisRepository.claimIfAbsent(eq(vacancyId), any(), eq(AnalysisOrigin.AUTOMATIC_DISCOVERY), eq(null)))
                .thenReturn(Optional.of(inProgressClaimEntity(vacancyId)));
        Vacancy vacancy = vacancy(vacancyId);
        when(vacancyRepository.findByIdWithCompany(vacancyId)).thenReturn(Optional.of(vacancy));
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(jobOffer());
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContextSnapshot(mock(CandidateProfile.class)));
    }

    private void givenSuccessfulNotificationDelivery(UUID vacancyId) {
        Vacancy vacancy = vacancy(vacancyId);
        lenient().when(vacancyRepository.findByIdWithCompany(vacancyId)).thenReturn(Optional.of(vacancy));
        // Deliberately any(): a test may already have its own findByIdWithCompany stub for a
        // different Vacancy instance (e.g. one used earlier to build the JobOffer for the AI
        // call) - this must match regardless of which instance loadVacancyOrThrow actually returns.
        when(jobNotificationFactory.createCompactRecommendation(any(), any(), eq(RECIPIENT_CHAT_ID))).thenReturn(notification(vacancyId));
        NotificationDelivery pendingDelivery = delivery(vacancyId, NotificationDeliveryStatus.PENDING);
        when(notificationDeliveryRepository.reserve(eq(vacancyId), eq(RECIPIENT_CHAT_ID), any()))
                .thenReturn(NotificationReservationResult.reserved(pendingDelivery));
        when(jobNotificationPort.sendCompactRecommendation(any())).thenReturn(JobNotificationResult.accepted("msg-1"));
        when(notificationDeliveryRepository.markSent(pendingDelivery.id(), NOW))
                .thenReturn(NotificationDeliveryTransitionResult.updated(
                        new NotificationDelivery(pendingDelivery.id(), vacancyId, RECIPIENT_CHAT_ID, NotificationDeliveryStatus.SENT, NOW, NOW, null, null)));
    }

    private VacancyRecommendationTaskEntity task(VacancyRecommendationTaskStatus status, int attemptCount) {
        return VacancyRecommendationTaskEntity.builder()
                .id(UUID.randomUUID())
                .vacancyId(UUID.randomUUID())
                .status(status)
                .attemptCount(attemptCount)
                .nextAttemptAt(NOW)
                .build();
    }

    private JobAnalysisEntity manuallyReviewedEntity() {
        return manuallyReviewedEntity(UUID.randomUUID());
    }

    private JobAnalysisEntity manuallyReviewedEntity(UUID vacancyId) {
        return analysisEntity(vacancyId, AnalysisStatus.COMPLETED, AnalysisOrigin.AUTOMATIC_DISCOVERY, NOW, 90);
    }

    private JobAnalysisEntity inProgressClaimEntity(UUID vacancyId) {
        return JobAnalysisEntity.builder()
                .id(UUID.randomUUID())
                .vacancyId(vacancyId)
                .status(AnalysisStatus.IN_PROGRESS)
                .analysisOrigin(AnalysisOrigin.AUTOMATIC_DISCOVERY)
                .pros(List.of()).cons(List.of()).missingRequiredSkills(List.of()).missingPreferredSkills(List.of())
                .build();
    }

    private JobAnalysisEntity analysisEntity(
            UUID vacancyId, AnalysisStatus status, AnalysisOrigin origin, Instant manuallyReviewedAt, int score) {
        return JobAnalysisEntity.builder()
                .id(UUID.randomUUID())
                .vacancyId(vacancyId)
                .status(status)
                .score(score)
                .summary("Solid match")
                .pros(List.of("Java"))
                .cons(List.of())
                .missingRequiredSkills(List.of())
                .missingPreferredSkills(List.of())
                .experienceAssessment("Meets requirement")
                .preferencesAssessment("Remote preference matches")
                .analysisVersion(1)
                .analysisOrigin(origin)
                .manuallyReviewedAt(manuallyReviewedAt)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    private Vacancy vacancy(UUID vacancyId) {
        Company company = Company.builder().name("Acme Corp").build();
        Vacancy vacancy = Vacancy.builder()
                .company(company)
                .title("Backend Engineer")
                .url("https://example.com/job")
                .source("remoteok")
                .build();
        vacancy.setId(vacancyId);
        return vacancy;
    }

    private JobOffer jobOffer() {
        return new JobOffer("job-1", "Backend Engineer", "Acme Corp", "Remote", null, "desc", "https://example.com/job", "remoteok");
    }

    private CandidateContextSnapshot candidateContextSnapshot(CandidateProfile profile) {
        return new CandidateContextSnapshot(UUID.randomUUID(), "primary", 0L, profile, Optional.empty());
    }

    private JobAnalysis analysis(int score) {
        return new JobAnalysis(score, List.of("Java"), List.of(), List.of(), List.of(),
                "6 years vs. no stated requirement.", "Remote preference matches.", "Solid match");
    }

    private CompactVacancyRecommendation notification(UUID vacancyId) {
        return new CompactVacancyRecommendation(vacancyId, RECIPIENT_CHAT_ID, "Backend Engineer", "Acme Corp", "https://example.com/job",
                90, "Solid match", List.of("Java"), List.of(), null, null, null);
    }

    private NotificationDelivery delivery(UUID vacancyId, NotificationDeliveryStatus status) {
        UUID id = UUID.randomUUID();
        return switch (status) {
            case PENDING -> new NotificationDelivery(id, vacancyId, RECIPIENT_CHAT_ID, status, NOW, null, null, null);
            case SENT -> new NotificationDelivery(id, vacancyId, RECIPIENT_CHAT_ID, status, NOW, NOW, null, null);
            case FAILED -> new NotificationDelivery(id, vacancyId, RECIPIENT_CHAT_ID, status, NOW, null, NOW, "TEMPORARY_FAILURE");
        };
    }
}
