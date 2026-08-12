package com.darya.jobassistant.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.ai.entity.AnalysisStatus;
import com.darya.jobassistant.ai.entity.JobAnalysisEntity;
import com.darya.jobassistant.ai.exception.JobAnalysisException;
import com.darya.jobassistant.ai.model.AnalysisOrigin;
import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.ai.model.JobAnalysisModelVersion;
import com.darya.jobassistant.ai.model.PersistedJobAnalysis;
import com.darya.jobassistant.ai.repository.JobAnalysisRepository;
import com.darya.jobassistant.candidatecontext.CandidateContextProvider;
import com.darya.jobassistant.candidatecontext.CandidateContextSnapshot;
import com.darya.jobassistant.candidatecontext.CareerHistoryAvailability;
import com.darya.jobassistant.candidatecontext.analysis.CandidateContextForAnalysis;
import com.darya.jobassistant.candidatecontext.analysis.CandidateContextForAnalysisSelector;
import com.darya.jobassistant.candidatecontext.analysis.CandidateContextSelectionMetadata;
import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateLanguageFacts;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateProfileFacts;
import com.darya.jobassistant.candidates.CandidateSkillFacts;
import com.darya.jobassistant.candidates.migration.CandidateProfileAnalysisAssembler;
import com.darya.jobassistant.candidates.SkillProficiency;
import com.darya.jobassistant.integrations.ai.openai.JobAnalysisService;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.integrations.notifier.JobNotification;
import com.darya.jobassistant.integrations.notifier.JobNotificationException;
import com.darya.jobassistant.integrations.notifier.JobNotificationFactory;
import com.darya.jobassistant.integrations.notifier.JobNotificationFailureType;
import com.darya.jobassistant.integrations.notifier.JobNotificationPort;
import com.darya.jobassistant.integrations.notifier.JobNotificationResult;
import com.darya.jobassistant.monitoring.dto.JobMonitoringCommand;
import com.darya.jobassistant.monitoring.dto.JobMonitoringResult;
import com.darya.jobassistant.notifications.dto.NotificationDelivery;
import com.darya.jobassistant.notifications.dto.NotificationDeliveryTransitionResult;
import com.darya.jobassistant.notifications.dto.NotificationReservationResult;
import com.darya.jobassistant.notifications.entity.NotificationDeliveryStatus;
import com.darya.jobassistant.notifications.query.JobNotificationCandidate;
import com.darya.jobassistant.notifications.query.JobNotificationCandidateQueryPort;
import com.darya.jobassistant.notifications.repository.NotificationDeliveryRepository;
import com.darya.jobassistant.vacancies.dto.VacancyIngestionResult;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.mapper.VacancyJobOfferMapper;
import com.darya.jobassistant.vacancies.service.VacancyIngestionService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobMonitoringServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Long RECIPIENT_CHAT_ID = 12345L;

    @Mock
    private VacancyIngestionService vacancyIngestionService;
    @Mock
    private VacancyJobOfferMapper vacancyJobOfferMapper;
    @Mock
    private CandidateContextProvider candidateContextProvider;
    @Mock
    private CandidateContextForAnalysisSelector candidateContextForAnalysisSelector;
    @Mock
    private JobAnalysisService jobAnalysisService;
    @Mock
    private JobAnalysisRepository jobAnalysisRepository;
    @Mock
    private JobNotificationCandidateQueryPort jobNotificationCandidateQueryPort;
    @Mock
    private JobNotificationFactory jobNotificationFactory;
    @Mock
    private JobNotificationPort jobNotificationPort;
    @Mock
    private NotificationDeliveryRepository notificationDeliveryRepository;

    private final Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
    private final CandidateProfileFacts profileFacts = new CandidateProfileFacts(
            "Backend Engineer",
            "Senior",
            List.of(new CandidateSkillFacts("Java", null, null, SkillProficiency.WORKING)),
            List.of(new CandidateLanguageFacts("English", null)),
            5,
            new CandidatePreferences(null, "Remote", null, List.of(), false, List.of(), null, "Product", null, null));
    /** Derived from {@link #profileFacts} so {@link #analysisContext} can never drift from it. */
    private final CandidateProfile profile = CandidateProfileAnalysisAssembler.toAnalysisProfile(profileFacts);
    private final CandidateContextSnapshot candidateContext =
            new CandidateContextSnapshot(UUID.randomUUID(), "primary", 0L, profileFacts, Optional.empty());
    private final CandidateContextForAnalysis analysisContext = new CandidateContextForAnalysis(
            profile, CareerHistoryAvailability.NOT_PROVIDED, List.of(),
            CandidateContextSelectionMetadata.empty(CareerHistoryAvailability.NOT_PROVIDED, null));

    private JobMonitoringService service;

    @BeforeEach
    void setUp() {
        service = new JobMonitoringService(
                List.of(), vacancyIngestionService, vacancyJobOfferMapper, candidateContextProvider,
                candidateContextForAnalysisSelector, jobAnalysisService, jobAnalysisRepository,
                jobNotificationCandidateQueryPort, jobNotificationFactory, jobNotificationPort,
                notificationDeliveryRepository, clock);
        when(candidateContextForAnalysisSelector.select(any(), any())).thenReturn(analysisContext);
    }

    // A. Candidate profile is loaded exactly once
    @Test
    void monitor_loadsCandidateProfileExactlyOnce() {
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        when(vacancyIngestionService.ingest(any())).thenReturn(VacancyIngestionResult.empty());
        when(jobNotificationCandidateQueryPort.findCandidates(any(), anyInt(), anyInt())).thenReturn(List.of());

        service.monitor(command(50, 5));

        verify(candidateContextProvider, times(1)).loadCurrentContext();
    }

    // B. Profile failure prevents ingestion and propagates safely
    @Test
    void monitor_profileLoadingFails_preventsIngestionAndPropagatesSafely() {
        when(candidateContextProvider.loadCurrentContext()).thenThrow(new IllegalStateException("profile config missing"));

        assertThatThrownBy(() -> service.monitor(command(50, 5)))
                .isInstanceOf(JobMonitoringException.class)
                .hasMessage("Unable to load candidate profile for job monitoring");

        verifyNoInteractions(vacancyIngestionService, jobAnalysisService, jobAnalysisRepository,
                jobNotificationCandidateQueryPort, jobNotificationFactory, jobNotificationPort, notificationDeliveryRepository);
    }

    // C. Empty ingestion still queries the notification backlog
    @Test
    void monitor_emptyIngestion_stillQueriesNotificationBacklog() {
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        when(vacancyIngestionService.ingest(any())).thenReturn(VacancyIngestionResult.empty());
        when(jobNotificationCandidateQueryPort.findCandidates(any(), anyInt(), anyInt())).thenReturn(List.of());

        JobMonitoringResult result = service.monitor(command(50, 5));

        verify(jobNotificationCandidateQueryPort).findCandidates(RECIPIENT_CHAT_ID, 50, 5);
        assertThat(result).isEqualTo(new JobMonitoringResult(0, 0, 0, 0, 0, 0));
        verifyNoInteractions(jobAnalysisService, jobAnalysisRepository);
    }

    // D. No new vacancies but an existing backlog candidate is reserved and sent
    @Test
    void monitor_noNewVacancies_existingBacklogCandidateIsReservedAndSent() {
        Vacancy backlogVacancy = vacancy();
        JobAnalysis analysis = analysis(90);
        JobNotificationCandidate candidate = candidateFor(backlogVacancy, analysis);
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        when(vacancyIngestionService.ingest(any())).thenReturn(VacancyIngestionResult.empty());
        when(jobNotificationCandidateQueryPort.findCandidates(any(), anyInt(), anyInt())).thenReturn(List.of(candidate));
        stubSuccessfulSend(backlogVacancy, analysis);

        JobMonitoringResult result = service.monitor(command(50, 5));

        assertThat(result.persistedCount()).isZero();
        assertThat(result.matchedCount()).isEqualTo(1);
        assertThat(result.notifiedCount()).isEqualTo(1);
        verify(notificationDeliveryRepository).reserve(backlogVacancy.getId(), RECIPIENT_CHAT_ID, FIXED_INSTANT);
    }

    // E. Every newly persisted Vacancy is analyzed in ingestion order
    @Test
    void monitor_analyzesEveryNewVacancyInIngestionOrder() {
        Vacancy first = vacancy();
        Vacancy second = vacancy();
        JobOffer firstOffer = offer("1");
        JobOffer secondOffer = offer("2");
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        when(vacancyIngestionService.ingest(any())).thenReturn(new VacancyIngestionResult(2, List.of(first, second), 0));
        when(vacancyJobOfferMapper.toJobOffer(first)).thenReturn(firstOffer);
        when(vacancyJobOfferMapper.toJobOffer(second)).thenReturn(secondOffer);
        when(jobAnalysisService.analyze(analysisContext, firstOffer)).thenReturn(analysis(10));
        when(jobAnalysisService.analyze(analysisContext, secondOffer)).thenReturn(analysis(20));
        when(jobNotificationCandidateQueryPort.findCandidates(any(), anyInt(), anyInt())).thenReturn(List.of());

        service.monitor(command(50, 5));

        InOrder inOrder = inOrder(jobAnalysisService);
        inOrder.verify(jobAnalysisService).analyze(analysisContext, firstOffer);
        inOrder.verify(jobAnalysisService).analyze(analysisContext, secondOffer);
    }

    // E2. A vacancy that already has a completed MANUAL analysis at the current version is
    // skipped entirely - monitoring never reanalyzes it or sends AI a request for it.
    @Test
    void monitor_vacancyWithExistingCurrentVersionManualAnalysis_skipsWithoutCallingAi() {
        Vacancy vacancy = vacancy();
        JobOffer offer = offer("1");
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        when(vacancyIngestionService.ingest(any())).thenReturn(new VacancyIngestionResult(1, List.of(vacancy), 0));
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(offer);
        when(jobAnalysisRepository.findByVacancyId(vacancy.getId()))
                .thenReturn(Optional.of(existingAnalysisEntity(JobAnalysisModelVersion.CURRENT, AnalysisOrigin.MANUAL)));
        when(jobNotificationCandidateQueryPort.findCandidates(any(), anyInt(), anyInt())).thenReturn(List.of());

        JobMonitoringResult result = service.monitor(command(50, 5));

        verifyNoInteractions(jobAnalysisService);
        verify(jobAnalysisRepository, never()).persist(any(), any(), any());
        assertThat(result.analyzedCount()).isZero();
        assertThat(result.failedCount()).isZero();
    }

    // E3. A vacancy with an outdated (stale-version) completed MANUAL analysis is also skipped -
    // monitoring never reanalyzes merely because analysisVersion < CURRENT.
    @Test
    void monitor_vacancyWithExistingStaleVersionManualAnalysis_skipsWithoutCallingAi() {
        Vacancy vacancy = vacancy();
        JobOffer offer = offer("1");
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        when(vacancyIngestionService.ingest(any())).thenReturn(new VacancyIngestionResult(1, List.of(vacancy), 0));
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(offer);
        when(jobAnalysisRepository.findByVacancyId(vacancy.getId()))
                .thenReturn(Optional.of(existingAnalysisEntity(1, AnalysisOrigin.MANUAL)));
        when(jobNotificationCandidateQueryPort.findCandidates(any(), anyInt(), anyInt())).thenReturn(List.of());

        JobMonitoringResult result = service.monitor(command(50, 5));

        verifyNoInteractions(jobAnalysisService);
        verify(jobAnalysisRepository, never()).persist(any(), any(), any());
        assertThat(result.analyzedCount()).isZero();
    }

    // E4. A vacancy with an existing completed LEGACY analysis is skipped the same way.
    @Test
    void monitor_vacancyWithExistingLegacyAnalysis_skipsWithoutCallingAi() {
        Vacancy vacancy = vacancy();
        JobOffer offer = offer("1");
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        when(vacancyIngestionService.ingest(any())).thenReturn(new VacancyIngestionResult(1, List.of(vacancy), 0));
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(offer);
        when(jobAnalysisRepository.findByVacancyId(vacancy.getId()))
                .thenReturn(Optional.of(existingAnalysisEntity(1, AnalysisOrigin.LEGACY)));
        when(jobNotificationCandidateQueryPort.findCandidates(any(), anyInt(), anyInt())).thenReturn(List.of());

        JobMonitoringResult result = service.monitor(command(50, 5));

        verifyNoInteractions(jobAnalysisService);
        verify(jobAnalysisRepository, never()).persist(any(), any(), any());
        assertThat(result.analyzedCount()).isZero();
    }

    // F. Successful analysis is persisted and increments analyzedCount only after persistence succeeds
    @Test
    void monitor_successfulAnalysis_isPersistedAndIncrementsAnalyzedCount() {
        Vacancy vacancy = vacancy();
        JobOffer offer = offer("1");
        JobAnalysis analysis = analysis(80);
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        when(vacancyIngestionService.ingest(any())).thenReturn(new VacancyIngestionResult(1, List.of(vacancy), 0));
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(offer);
        when(jobAnalysisService.analyze(analysisContext, offer)).thenReturn(analysis);
        when(jobNotificationCandidateQueryPort.findCandidates(any(), anyInt(), anyInt())).thenReturn(List.of());

        JobMonitoringResult result = service.monitor(command(50, 5));

        verify(jobAnalysisRepository).persist(vacancy.getId(), analysis, AnalysisOrigin.MONITORING);
        assertThat(result.analyzedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();
    }

    // G. Analysis failure increments failedCount once and remaining Vacancies continue
    @Test
    void monitor_analysisFailureForOneVacancy_incrementsFailedCountOnceAndContinuesWithRemaining() {
        Vacancy failing = vacancy();
        Vacancy succeeding = vacancy();
        JobOffer failingOffer = offer("1");
        JobOffer succeedingOffer = offer("2");
        JobAnalysis okAnalysis = analysis(20);
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        when(vacancyIngestionService.ingest(any())).thenReturn(new VacancyIngestionResult(2, List.of(failing, succeeding), 0));
        when(vacancyJobOfferMapper.toJobOffer(failing)).thenReturn(failingOffer);
        when(vacancyJobOfferMapper.toJobOffer(succeeding)).thenReturn(succeedingOffer);
        when(jobAnalysisService.analyze(analysisContext, failingOffer)).thenThrow(new JobAnalysisException("boom"));
        when(jobAnalysisService.analyze(analysisContext, succeedingOffer)).thenReturn(okAnalysis);
        when(jobNotificationCandidateQueryPort.findCandidates(any(), anyInt(), anyInt())).thenReturn(List.of());

        JobMonitoringResult result = service.monitor(command(50, 5));

        assertThat(result.analyzedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        verify(jobAnalysisRepository).persist(succeeding.getId(), okAnalysis, AnalysisOrigin.MONITORING);
        verify(jobAnalysisRepository, never()).persist(eq(failing.getId()), any(), any());
    }

    // H. JobAnalysis persistence failure increments failedCount once and remaining Vacancies continue
    @Test
    void monitor_analysisPersistenceFailureForOneVacancy_incrementsFailedCountOnceAndContinuesWithRemaining() {
        Vacancy failing = vacancy();
        Vacancy succeeding = vacancy();
        JobOffer failingOffer = offer("1");
        JobOffer succeedingOffer = offer("2");
        JobAnalysis failingAnalysis = analysis(70);
        JobAnalysis okAnalysis = analysis(20);
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        when(vacancyIngestionService.ingest(any())).thenReturn(new VacancyIngestionResult(2, List.of(failing, succeeding), 0));
        when(vacancyJobOfferMapper.toJobOffer(failing)).thenReturn(failingOffer);
        when(vacancyJobOfferMapper.toJobOffer(succeeding)).thenReturn(succeedingOffer);
        when(jobAnalysisService.analyze(analysisContext, failingOffer)).thenReturn(failingAnalysis);
        when(jobAnalysisService.analyze(analysisContext, succeedingOffer)).thenReturn(okAnalysis);
        when(jobAnalysisRepository.persist(failing.getId(), failingAnalysis, AnalysisOrigin.MONITORING))
                .thenThrow(new RuntimeException("db down"));
        when(jobNotificationCandidateQueryPort.findCandidates(any(), anyInt(), anyInt())).thenReturn(List.of());

        JobMonitoringResult result = service.monitor(command(50, 5));

        assertThat(result.analyzedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        verify(jobAnalysisRepository).persist(succeeding.getId(), okAnalysis, AnalysisOrigin.MONITORING);
    }

    // I. Backlog query occurs after all new analysis persistence attempts
    @Test
    void monitor_backlogQuery_occursAfterAllNewVacancyAnalysisAttempts() {
        Vacancy vacancy = vacancy();
        JobOffer offer = offer("1");
        JobAnalysis analysis = analysis(80);
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        when(vacancyIngestionService.ingest(any())).thenReturn(new VacancyIngestionResult(1, List.of(vacancy), 0));
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(offer);
        when(jobAnalysisService.analyze(analysisContext, offer)).thenReturn(analysis);
        when(jobNotificationCandidateQueryPort.findCandidates(any(), anyInt(), anyInt())).thenReturn(List.of());

        service.monitor(command(50, 5));

        InOrder inOrder = inOrder(jobAnalysisRepository, jobNotificationCandidateQueryPort);
        inOrder.verify(jobAnalysisRepository).persist(vacancy.getId(), analysis, AnalysisOrigin.MONITORING);
        inOrder.verify(jobNotificationCandidateQueryPort).findCandidates(any(), anyInt(), anyInt());
    }

    // J. Backlog query receives recipientChatId, minimumScore, and maxNotifications
    @Test
    void monitor_backlogQuery_receivesRecipientMinimumScoreAndMaxNotifications() {
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        when(vacancyIngestionService.ingest(any())).thenReturn(VacancyIngestionResult.empty());
        when(jobNotificationCandidateQueryPort.findCandidates(any(), anyInt(), anyInt())).thenReturn(List.of());

        service.monitor(command(65, 7));

        verify(jobNotificationCandidateQueryPort).findCandidates(RECIPIENT_CHAT_ID, 65, 7);
    }

    // K. matchedCount equals the number of returned backlog candidates
    @Test
    void monitor_matchedCount_equalsNumberOfReturnedBacklogCandidates() {
        Vacancy v1 = vacancy();
        Vacancy v2 = vacancy();
        Vacancy v3 = vacancy();
        JobAnalysis a1 = analysis(95);
        JobAnalysis a2 = analysis(90);
        JobAnalysis a3 = analysis(85);
        List<JobNotificationCandidate> candidates =
                List.of(candidateFor(v1, a1), candidateFor(v2, a2), candidateFor(v3, a3));
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        when(vacancyIngestionService.ingest(any())).thenReturn(VacancyIngestionResult.empty());
        when(jobNotificationCandidateQueryPort.findCandidates(any(), anyInt(), anyInt())).thenReturn(candidates);
        stubSuccessfulSend(v1, a1);
        stubSuccessfulSend(v2, a2);
        stubSuccessfulSend(v3, a3);

        JobMonitoringResult result = service.monitor(command(50, 5));

        assertThat(result.matchedCount()).isEqualTo(3);
    }

    // L. Returned backlog order is preserved during sending
    @Test
    void monitor_preservesBacklogOrderWhileSending() {
        Vacancy first = vacancy();
        Vacancy second = vacancy();
        Vacancy third = vacancy();
        // deliberately not in score order, to prove the service does not re-sort
        JobAnalysis firstAnalysis = analysis(60);
        JobAnalysis secondAnalysis = analysis(95);
        JobAnalysis thirdAnalysis = analysis(80);
        List<JobNotificationCandidate> candidates = List.of(
                candidateFor(first, firstAnalysis), candidateFor(second, secondAnalysis), candidateFor(third, thirdAnalysis));
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        when(vacancyIngestionService.ingest(any())).thenReturn(VacancyIngestionResult.empty());
        when(jobNotificationCandidateQueryPort.findCandidates(any(), anyInt(), anyInt())).thenReturn(candidates);
        stubSuccessfulSend(first, firstAnalysis);
        stubSuccessfulSend(second, secondAnalysis);
        stubSuccessfulSend(third, thirdAnalysis);

        service.monitor(command(50, 5));

        InOrder inOrder = inOrder(notificationDeliveryRepository);
        inOrder.verify(notificationDeliveryRepository).reserve(eq(first.getId()), any(), any());
        inOrder.verify(notificationDeliveryRepository).reserve(eq(second.getId()), any(), any());
        inOrder.verify(notificationDeliveryRepository).reserve(eq(third.getId()), any(), any());
    }

    // M. JobNotificationFactory is called before reservation
    @Test
    void monitor_callsNotificationFactoryBeforeReservingDelivery() {
        Vacancy vacancy = vacancy();
        JobAnalysis analysis = analysis(90);
        JobNotificationCandidate candidate = candidateFor(vacancy, analysis);
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        when(vacancyIngestionService.ingest(any())).thenReturn(VacancyIngestionResult.empty());
        when(jobNotificationCandidateQueryPort.findCandidates(any(), anyInt(), anyInt())).thenReturn(List.of(candidate));
        stubSuccessfulSend(vacancy, analysis);

        service.monitor(command(50, 5));

        InOrder inOrder = inOrder(jobNotificationFactory, notificationDeliveryRepository);
        inOrder.verify(jobNotificationFactory).create(vacancy, analysis, RECIPIENT_CHAT_ID);
        inOrder.verify(notificationDeliveryRepository).reserve(vacancy.getId(), RECIPIENT_CHAT_ID, FIXED_INSTANT);
    }

    // N. Factory failure does not reserve delivery
    @Test
    void monitor_notificationFactoryFailure_doesNotReserveDeliveryAndIncrementsFailedCountOnce() {
        Vacancy vacancy = vacancy();
        JobAnalysis analysis = analysis(90);
        JobNotificationCandidate candidate = candidateFor(vacancy, analysis);
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        when(vacancyIngestionService.ingest(any())).thenReturn(VacancyIngestionResult.empty());
        when(jobNotificationCandidateQueryPort.findCandidates(any(), anyInt(), anyInt())).thenReturn(List.of(candidate));
        when(jobNotificationFactory.create(vacancy, analysis, RECIPIENT_CHAT_ID))
                .thenThrow(new IllegalArgumentException("bad data"));

        JobMonitoringResult result = service.monitor(command(50, 5));

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.notifiedCount()).isZero();
        verify(notificationDeliveryRepository, never()).reserve(any(), any(), any());
    }

    // O. ALREADY_EXISTS does not send, does not fail, and processing continues
    @Test
    void monitor_alreadyExistsReservation_doesNotSendDoesNotFailAndContinuesToNextCandidate() {
        Vacancy v1 = vacancy();
        Vacancy v2 = vacancy();
        JobAnalysis a1 = analysis(95);
        JobAnalysis a2 = analysis(90);
        JobNotificationCandidate c1 = candidateFor(v1, a1);
        JobNotificationCandidate c2 = candidateFor(v2, a2);
        JobNotification n1 = notification();
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        when(vacancyIngestionService.ingest(any())).thenReturn(VacancyIngestionResult.empty());
        when(jobNotificationCandidateQueryPort.findCandidates(any(), anyInt(), anyInt())).thenReturn(List.of(c1, c2));
        when(jobNotificationFactory.create(v1, a1, RECIPIENT_CHAT_ID)).thenReturn(n1);
        when(notificationDeliveryRepository.reserve(v1.getId(), RECIPIENT_CHAT_ID, FIXED_INSTANT))
                .thenReturn(NotificationReservationResult.alreadyExists());
        stubSuccessfulSend(v2, a2);

        JobMonitoringResult result = service.monitor(command(50, 5));

        assertThat(result.notifiedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();
        verify(jobNotificationPort, never()).send(n1);
        verify(jobNotificationPort, times(1)).send(any());
    }

    // P. Successful delivery follows factory -> reserve -> send -> markSent
    @Test
    void monitor_successfulDelivery_followsFactoryThenReserveThenSendThenMarkSent() {
        Vacancy vacancy = vacancy();
        JobAnalysis analysis = analysis(90);
        JobNotificationCandidate candidate = candidateFor(vacancy, analysis);
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        when(vacancyIngestionService.ingest(any())).thenReturn(VacancyIngestionResult.empty());
        when(jobNotificationCandidateQueryPort.findCandidates(any(), anyInt(), anyInt())).thenReturn(List.of(candidate));
        NotificationDelivery pending = stubSuccessfulSend(vacancy, analysis);

        JobMonitoringResult result = service.monitor(command(50, 5));

        assertThat(result.notifiedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();
        InOrder inOrder = inOrder(jobNotificationFactory, notificationDeliveryRepository, jobNotificationPort);
        inOrder.verify(jobNotificationFactory).create(vacancy, analysis, RECIPIENT_CHAT_ID);
        inOrder.verify(notificationDeliveryRepository).reserve(vacancy.getId(), RECIPIENT_CHAT_ID, FIXED_INSTANT);
        inOrder.verify(jobNotificationPort).send(any());
        inOrder.verify(notificationDeliveryRepository).markSent(pending.id(), FIXED_INSTANT);
    }

    // Q. JobNotificationException follows factory -> reserve -> send failure -> markFailed
    @Test
    void monitor_jobNotificationException_followsFactoryThenReserveThenSendFailureThenMarkFailed() {
        Vacancy vacancy = vacancy();
        JobAnalysis analysis = analysis(90);
        JobNotificationCandidate candidate = candidateFor(vacancy, analysis);
        JobNotification notification = notification();
        NotificationDelivery pending = pendingDelivery(vacancy.getId());
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        when(vacancyIngestionService.ingest(any())).thenReturn(VacancyIngestionResult.empty());
        when(jobNotificationCandidateQueryPort.findCandidates(any(), anyInt(), anyInt())).thenReturn(List.of(candidate));
        when(jobNotificationFactory.create(vacancy, analysis, RECIPIENT_CHAT_ID)).thenReturn(notification);
        when(notificationDeliveryRepository.reserve(vacancy.getId(), RECIPIENT_CHAT_ID, FIXED_INSTANT))
                .thenReturn(NotificationReservationResult.reserved(pending));
        when(jobNotificationPort.send(notification))
                .thenThrow(new JobNotificationException(JobNotificationFailureType.PERMANENT_FAILURE, "cannot deliver"));
        when(notificationDeliveryRepository.markFailed(pending.id(), FIXED_INSTANT, "PERMANENT_FAILURE"))
                .thenReturn(NotificationDeliveryTransitionResult.updated(failedDelivery()));

        JobMonitoringResult result = service.monitor(command(50, 5));

        assertThat(result.notifiedCount()).isZero();
        assertThat(result.failedCount()).isEqualTo(1);
        InOrder inOrder = inOrder(jobNotificationFactory, notificationDeliveryRepository, jobNotificationPort);
        inOrder.verify(jobNotificationFactory).create(vacancy, analysis, RECIPIENT_CHAT_ID);
        inOrder.verify(notificationDeliveryRepository).reserve(vacancy.getId(), RECIPIENT_CHAT_ID, FIXED_INSTANT);
        inOrder.verify(jobNotificationPort).send(notification);
        inOrder.verify(notificationDeliveryRepository).markFailed(pending.id(), FIXED_INSTANT, "PERMANENT_FAILURE");
    }

    // R. markFailed receives only failureType.name(), never the raw exception message
    @Test
    void monitor_markFailed_receivesOnlyFailureTypeNameNeverTheExceptionMessage() {
        Vacancy vacancy = vacancy();
        JobAnalysis analysis = analysis(90);
        JobNotificationCandidate candidate = candidateFor(vacancy, analysis);
        JobNotification notification = notification();
        NotificationDelivery pending = pendingDelivery(vacancy.getId());
        String rawProviderMessage = "raw provider response: rate limit exceeded for chat 12345";
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        when(vacancyIngestionService.ingest(any())).thenReturn(VacancyIngestionResult.empty());
        when(jobNotificationCandidateQueryPort.findCandidates(any(), anyInt(), anyInt())).thenReturn(List.of(candidate));
        when(jobNotificationFactory.create(vacancy, analysis, RECIPIENT_CHAT_ID)).thenReturn(notification);
        when(notificationDeliveryRepository.reserve(vacancy.getId(), RECIPIENT_CHAT_ID, FIXED_INSTANT))
                .thenReturn(NotificationReservationResult.reserved(pending));
        when(jobNotificationPort.send(notification))
                .thenThrow(new JobNotificationException(JobNotificationFailureType.TEMPORARY_FAILURE, rawProviderMessage));
        when(notificationDeliveryRepository.markFailed(any(), any(), any()))
                .thenReturn(NotificationDeliveryTransitionResult.updated(failedDelivery()));

        service.monitor(command(50, 5));

        ArgumentCaptor<String> failureCodeCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationDeliveryRepository).markFailed(eq(pending.id()), eq(FIXED_INSTANT), failureCodeCaptor.capture());
        assertThat(failureCodeCaptor.getValue()).isEqualTo(JobNotificationFailureType.TEMPORARY_FAILURE.name());
        assertThat(failureCodeCaptor.getValue()).doesNotContain(rawProviderMessage);
    }

    // S. Send failure plus markFailed failure increments failedCount only once
    @Test
    void monitor_sendFailureAndMarkFailedFailure_incrementsFailedCountOnlyOnce() {
        Vacancy vacancy = vacancy();
        JobAnalysis analysis = analysis(90);
        JobNotificationCandidate candidate = candidateFor(vacancy, analysis);
        JobNotification notification = notification();
        NotificationDelivery pending = pendingDelivery(vacancy.getId());
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        when(vacancyIngestionService.ingest(any())).thenReturn(VacancyIngestionResult.empty());
        when(jobNotificationCandidateQueryPort.findCandidates(any(), anyInt(), anyInt())).thenReturn(List.of(candidate));
        when(jobNotificationFactory.create(vacancy, analysis, RECIPIENT_CHAT_ID)).thenReturn(notification);
        when(notificationDeliveryRepository.reserve(vacancy.getId(), RECIPIENT_CHAT_ID, FIXED_INSTANT))
                .thenReturn(NotificationReservationResult.reserved(pending));
        when(jobNotificationPort.send(notification))
                .thenThrow(new JobNotificationException(JobNotificationFailureType.TEMPORARY_FAILURE, "provider down"));
        when(notificationDeliveryRepository.markFailed(any(), any(), any())).thenThrow(new RuntimeException("db unavailable"));

        JobMonitoringResult result = service.monitor(command(50, 5));

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.notifiedCount()).isZero();
    }

    // T. Provider acceptance plus markSent failure increments both notifiedCount and failedCount
    // U. markFailed is not called after provider acceptance
    @Test
    void monitor_providerAcceptsButMarkSentFails_incrementsBothNotifiedAndFailedAndNeverCallsMarkFailed() {
        Vacancy vacancy = vacancy();
        JobAnalysis analysis = analysis(90);
        JobNotificationCandidate candidate = candidateFor(vacancy, analysis);
        JobNotification notification = notification();
        NotificationDelivery pending = pendingDelivery(vacancy.getId());
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        when(vacancyIngestionService.ingest(any())).thenReturn(VacancyIngestionResult.empty());
        when(jobNotificationCandidateQueryPort.findCandidates(any(), anyInt(), anyInt())).thenReturn(List.of(candidate));
        when(jobNotificationFactory.create(vacancy, analysis, RECIPIENT_CHAT_ID)).thenReturn(notification);
        when(notificationDeliveryRepository.reserve(vacancy.getId(), RECIPIENT_CHAT_ID, FIXED_INSTANT))
                .thenReturn(NotificationReservationResult.reserved(pending));
        when(jobNotificationPort.send(notification)).thenReturn(JobNotificationResult.accepted());
        when(notificationDeliveryRepository.markSent(pending.id(), FIXED_INSTANT))
                .thenReturn(NotificationDeliveryTransitionResult.notFound());

        JobMonitoringResult result = service.monitor(command(50, 5));

        assertThat(result.notifiedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        verify(jobNotificationPort, times(1)).send(notification);
        verify(notificationDeliveryRepository, never()).markFailed(any(), any(), any());
    }

    // V. Unexpected RuntimeException from sender marks FAILED with UNEXPECTED_FAILURE
    @Test
    void monitor_unexpectedRuntimeSendFailure_marksFailedWithUnexpectedFailureCode() {
        Vacancy vacancy = vacancy();
        JobAnalysis analysis = analysis(90);
        JobNotificationCandidate candidate = candidateFor(vacancy, analysis);
        JobNotification notification = notification();
        NotificationDelivery pending = pendingDelivery(vacancy.getId());
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        when(vacancyIngestionService.ingest(any())).thenReturn(VacancyIngestionResult.empty());
        when(jobNotificationCandidateQueryPort.findCandidates(any(), anyInt(), anyInt())).thenReturn(List.of(candidate));
        when(jobNotificationFactory.create(vacancy, analysis, RECIPIENT_CHAT_ID)).thenReturn(notification);
        when(notificationDeliveryRepository.reserve(vacancy.getId(), RECIPIENT_CHAT_ID, FIXED_INSTANT))
                .thenReturn(NotificationReservationResult.reserved(pending));
        when(jobNotificationPort.send(notification)).thenThrow(new RuntimeException("unexpected failure"));
        when(notificationDeliveryRepository.markFailed(pending.id(), FIXED_INSTANT, JobNotificationFailureType.UNEXPECTED_FAILURE.name()))
                .thenReturn(NotificationDeliveryTransitionResult.updated(failedDelivery()));

        JobMonitoringResult result = service.monitor(command(50, 5));

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.notifiedCount()).isZero();
        verify(notificationDeliveryRepository)
                .markFailed(pending.id(), FIXED_INSTANT, JobNotificationFailureType.UNEXPECTED_FAILURE.name());
    }

    // W. Fixed Clock values are passed to reserve, markSent, and markFailed - see P/Q/V, all use FIXED_INSTANT

    // X. No notification provider calls exceed the number of returned backlog candidates
    @Test
    void monitor_notificationProviderCallCount_neverExceedsReturnedBacklogCandidates() {
        Vacancy v1 = vacancy();
        Vacancy v2 = vacancy();
        JobAnalysis a1 = analysis(95);
        JobAnalysis a2 = analysis(90);
        List<JobNotificationCandidate> candidates = List.of(candidateFor(v1, a1), candidateFor(v2, a2));
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        when(vacancyIngestionService.ingest(any())).thenReturn(VacancyIngestionResult.empty());
        when(jobNotificationCandidateQueryPort.findCandidates(any(), anyInt(), anyInt())).thenReturn(candidates);
        stubSuccessfulSend(v1, a1);
        stubSuccessfulSend(v2, a2);

        service.monitor(command(50, 2));

        verify(jobNotificationPort, times(2)).send(any());
    }

    // Y. Exact counter semantics verified in a mixed scenario
    @Test
    void monitor_mixedScenario_countersReflectExactSemantics() {
        Vacancy analyzeOk = vacancy();
        Vacancy analyzeFail = vacancy();
        Vacancy analyzeOk2 = vacancy();
        JobOffer offer1 = offer("1");
        JobOffer offer2 = offer("2");
        JobOffer offer3 = offer("3");
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        when(vacancyIngestionService.ingest(any()))
                .thenReturn(new VacancyIngestionResult(5, List.of(analyzeOk, analyzeFail, analyzeOk2), 0));
        when(vacancyJobOfferMapper.toJobOffer(analyzeOk)).thenReturn(offer1);
        when(vacancyJobOfferMapper.toJobOffer(analyzeFail)).thenReturn(offer2);
        when(vacancyJobOfferMapper.toJobOffer(analyzeOk2)).thenReturn(offer3);
        JobAnalysis analysis1 = analysis(40);
        JobAnalysis analysis3 = analysis(45);
        when(jobAnalysisService.analyze(analysisContext, offer1)).thenReturn(analysis1);
        when(jobAnalysisService.analyze(analysisContext, offer2)).thenThrow(new JobAnalysisException("boom"));
        when(jobAnalysisService.analyze(analysisContext, offer3)).thenReturn(analysis3);

        Vacancy sendOk = vacancy();
        Vacancy sendFail = vacancy();
        JobAnalysis okAnalysis = analysis(90);
        JobAnalysis failAnalysis = analysis(85);
        JobNotificationCandidate okCandidate = candidateFor(sendOk, okAnalysis);
        JobNotificationCandidate failCandidate = candidateFor(sendFail, failAnalysis);
        when(jobNotificationCandidateQueryPort.findCandidates(RECIPIENT_CHAT_ID, 50, 5))
                .thenReturn(List.of(okCandidate, failCandidate));
        stubSuccessfulSend(sendOk, okAnalysis);
        JobNotification failNotification = notification();
        NotificationDelivery failPending = pendingDelivery(sendFail.getId());
        when(jobNotificationFactory.create(sendFail, failAnalysis, RECIPIENT_CHAT_ID)).thenReturn(failNotification);
        when(notificationDeliveryRepository.reserve(sendFail.getId(), RECIPIENT_CHAT_ID, FIXED_INSTANT))
                .thenReturn(NotificationReservationResult.reserved(failPending));
        when(jobNotificationPort.send(failNotification))
                .thenThrow(new JobNotificationException(JobNotificationFailureType.PERMANENT_FAILURE, "no"));
        when(notificationDeliveryRepository.markFailed(failPending.id(), FIXED_INSTANT, "PERMANENT_FAILURE"))
                .thenReturn(NotificationDeliveryTransitionResult.updated(failedDelivery()));

        JobMonitoringResult result = service.monitor(command(50, 5));

        assertThat(result.fetchedCount()).isEqualTo(5);
        assertThat(result.persistedCount()).isEqualTo(3);
        assertThat(result.analyzedCount()).isEqualTo(2);
        assertThat(result.matchedCount()).isEqualTo(2);
        assertThat(result.notifiedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(2);
    }

    private NotificationDelivery stubSuccessfulSend(Vacancy vacancy, JobAnalysis analysis) {
        JobNotification notification = notification();
        NotificationDelivery pending = pendingDelivery(vacancy.getId());
        when(jobNotificationFactory.create(vacancy, analysis, RECIPIENT_CHAT_ID)).thenReturn(notification);
        when(notificationDeliveryRepository.reserve(vacancy.getId(), RECIPIENT_CHAT_ID, FIXED_INSTANT))
                .thenReturn(NotificationReservationResult.reserved(pending));
        when(jobNotificationPort.send(notification)).thenReturn(JobNotificationResult.accepted());
        when(notificationDeliveryRepository.markSent(pending.id(), FIXED_INSTANT))
                .thenReturn(NotificationDeliveryTransitionResult.updated(sentDelivery()));
        return pending;
    }

    private JobMonitoringCommand command(int minScore, int maxNotifications) {
        return new JobMonitoringCommand("java", minScore, maxNotifications, RECIPIENT_CHAT_ID);
    }

    private Vacancy vacancy() {
        return Vacancy.builder().id(UUID.randomUUID()).build();
    }

    private JobOffer offer(String id) {
        return new JobOffer(id, "Backend Engineer", "Acme", null, null, "Java role", "https://example.com/" + id, "remoteok");
    }

    private JobAnalysis analysis(int score) {
        return new JobAnalysis(
                score, List.of(), List.of(), List.of(), List.of(),
                "No years stated; seniority appears broadly aligned.", "No preference conflicts detected.", "Great match");
    }

    private JobAnalysisEntity existingAnalysisEntity(int version, AnalysisOrigin origin) {
        return JobAnalysisEntity.builder()
                .id(UUID.randomUUID())
                .status(AnalysisStatus.COMPLETED)
                .score(80)
                .summary("Already analyzed")
                .pros(List.of())
                .cons(List.of())
                .missingRequiredSkills(List.of())
                .missingPreferredSkills(List.of())
                .experienceAssessment("Not assessed.")
                .preferencesAssessment("Not assessed.")
                .analysisVersion(version)
                .analysisOrigin(origin)
                .build();
    }

    private JobNotificationCandidate candidateFor(Vacancy vacancy, JobAnalysis analysis) {
        return new JobNotificationCandidate(
                vacancy, new PersistedJobAnalysis(
                        UUID.randomUUID(), vacancy.getId(), analysis, JobAnalysisModelVersion.CURRENT, AnalysisOrigin.MONITORING,
                        FIXED_INSTANT, null));
    }

    private JobNotification notification() {
        return new JobNotification(
                UUID.randomUUID(), RECIPIENT_CHAT_ID, "Backend Engineer", "Acme", "https://example.com/job",
                analysis(90));
    }

    private NotificationDelivery pendingDelivery(UUID vacancyId) {
        return new NotificationDelivery(
                UUID.randomUUID(), vacancyId, RECIPIENT_CHAT_ID, NotificationDeliveryStatus.PENDING, FIXED_INSTANT, null, null, null);
    }

    private NotificationDelivery sentDelivery() {
        return new NotificationDelivery(
                UUID.randomUUID(), UUID.randomUUID(), RECIPIENT_CHAT_ID, NotificationDeliveryStatus.SENT,
                FIXED_INSTANT, FIXED_INSTANT, null, null);
    }

    private NotificationDelivery failedDelivery() {
        return new NotificationDelivery(
                UUID.randomUUID(), UUID.randomUUID(), RECIPIENT_CHAT_ID, NotificationDeliveryStatus.FAILED,
                FIXED_INSTANT, null, FIXED_INSTANT, "SOME_CODE");
    }
}
