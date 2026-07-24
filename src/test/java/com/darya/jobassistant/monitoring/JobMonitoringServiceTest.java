package com.darya.jobassistant.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.ai.exception.JobAnalysisException;
import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateProfileProvider;
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
import com.darya.jobassistant.notifications.repository.NotificationDeliveryRepository;
import com.darya.jobassistant.vacancies.dto.VacancyIngestionResult;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.mapper.VacancyJobOfferMapper;
import com.darya.jobassistant.vacancies.service.VacancyIngestionService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private CandidateProfileProvider candidateProfileProvider;
    @Mock
    private JobAnalysisService jobAnalysisService;
    @Mock
    private JobNotificationFactory jobNotificationFactory;
    @Mock
    private JobNotificationPort jobNotificationPort;
    @Mock
    private NotificationDeliveryRepository notificationDeliveryRepository;

    private final Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
    private final CandidateProfile profile =
            new CandidateProfile("Backend Engineer", List.of("Java"), List.of("English"), 5, "Product", "Remote");

    private JobMonitoringService service;

    @BeforeEach
    void setUp() {
        service = new JobMonitoringService(
                List.of(), vacancyIngestionService, vacancyJobOfferMapper, candidateProfileProvider,
                jobAnalysisService, jobNotificationFactory, jobNotificationPort, notificationDeliveryRepository, clock);
    }

    // A. Empty ingestion result
    @Test
    void monitor_emptyIngestionResult_returnsZeroCountersAndSkipsDownstreamCalls() {
        when(vacancyIngestionService.ingest(any())).thenReturn(VacancyIngestionResult.empty());

        JobMonitoringResult result = service.monitor(command(50, 5));

        assertThat(result).isEqualTo(new JobMonitoringResult(0, 0, 0, 0, 0, 0));
        verifyNoInteractions(candidateProfileProvider, jobAnalysisService, jobNotificationFactory,
                jobNotificationPort, notificationDeliveryRepository);
    }

    // B. All below threshold
    @Test
    void monitor_allVacanciesBelowThreshold_analyzesAllButNoMatchesOrNotifications() {
        Vacancy v1 = vacancy();
        Vacancy v2 = vacancy();
        when(vacancyIngestionService.ingest(any())).thenReturn(new VacancyIngestionResult(2, List.of(v1, v2), 0));
        when(candidateProfileProvider.getProfile()).thenReturn(profile);
        when(vacancyJobOfferMapper.toJobOffer(v1)).thenReturn(offer("1"));
        when(vacancyJobOfferMapper.toJobOffer(v2)).thenReturn(offer("2"));
        when(jobAnalysisService.analyze(eq(profile), any())).thenReturn(analysis(30));

        JobMonitoringResult result = service.monitor(command(50, 5));

        assertThat(result.analyzedCount()).isEqualTo(2);
        assertThat(result.matchedCount()).isZero();
        assertThat(result.notifiedCount()).isZero();
        assertThat(result.failedCount()).isZero();
        verifyNoInteractions(notificationDeliveryRepository, jobNotificationPort, jobNotificationFactory);
    }

    // C. Ranked by score descending
    @Test
    void monitor_matchingVacancies_areNotifiedInScoreDescendingOrder() {
        Vacancy low = vacancy();
        Vacancy high = vacancy();
        JobOffer lowOffer = offer("low");
        JobOffer highOffer = offer("high");
        when(vacancyIngestionService.ingest(any())).thenReturn(new VacancyIngestionResult(2, List.of(low, high), 0));
        when(candidateProfileProvider.getProfile()).thenReturn(profile);
        when(vacancyJobOfferMapper.toJobOffer(low)).thenReturn(lowOffer);
        when(vacancyJobOfferMapper.toJobOffer(high)).thenReturn(highOffer);
        when(jobAnalysisService.analyze(profile, lowOffer)).thenReturn(analysis(60));
        when(jobAnalysisService.analyze(profile, highOffer)).thenReturn(analysis(95));
        stubGenericSuccessfulSend();

        service.monitor(command(50, 5));

        InOrder inOrder = inOrder(notificationDeliveryRepository);
        inOrder.verify(notificationDeliveryRepository).reserve(eq(high.getId()), any(), any());
        inOrder.verify(notificationDeliveryRepository).reserve(eq(low.getId()), any(), any());
    }

    // D. Equal scores preserve ingestion order
    @Test
    void monitor_equalScores_preserveIngestionOrderAsTiebreaker() {
        Vacancy first = vacancy();
        Vacancy second = vacancy();
        JobOffer firstOffer = offer("1");
        JobOffer secondOffer = offer("2");
        when(vacancyIngestionService.ingest(any())).thenReturn(new VacancyIngestionResult(2, List.of(first, second), 0));
        when(candidateProfileProvider.getProfile()).thenReturn(profile);
        when(vacancyJobOfferMapper.toJobOffer(first)).thenReturn(firstOffer);
        when(vacancyJobOfferMapper.toJobOffer(second)).thenReturn(secondOffer);
        when(jobAnalysisService.analyze(profile, firstOffer)).thenReturn(analysis(80));
        when(jobAnalysisService.analyze(profile, secondOffer)).thenReturn(analysis(80));
        stubGenericSuccessfulSend();

        service.monitor(command(50, 5));

        InOrder inOrder = inOrder(notificationDeliveryRepository);
        inOrder.verify(notificationDeliveryRepository).reserve(eq(first.getId()), any(), any());
        inOrder.verify(notificationDeliveryRepository).reserve(eq(second.getId()), any(), any());
    }

    // E. maxNotifications bounds successful provider calls
    @Test
    void monitor_maxNotifications_stopsSuccessfulSendsAtTheLimit() {
        Vacancy v1 = vacancy();
        Vacancy v2 = vacancy();
        Vacancy v3 = vacancy();
        JobOffer o1 = offer("1");
        JobOffer o2 = offer("2");
        JobOffer o3 = offer("3");
        when(vacancyIngestionService.ingest(any())).thenReturn(new VacancyIngestionResult(3, List.of(v1, v2, v3), 0));
        when(candidateProfileProvider.getProfile()).thenReturn(profile);
        when(vacancyJobOfferMapper.toJobOffer(v1)).thenReturn(o1);
        when(vacancyJobOfferMapper.toJobOffer(v2)).thenReturn(o2);
        when(vacancyJobOfferMapper.toJobOffer(v3)).thenReturn(o3);
        when(jobAnalysisService.analyze(profile, o1)).thenReturn(analysis(95));
        when(jobAnalysisService.analyze(profile, o2)).thenReturn(analysis(90));
        when(jobAnalysisService.analyze(profile, o3)).thenReturn(analysis(85));
        stubGenericSuccessfulSend();

        JobMonitoringResult result = service.monitor(command(50, 2));

        assertThat(result.matchedCount()).isEqualTo(3);
        assertThat(result.notifiedCount()).isEqualTo(2);
        verify(jobNotificationPort, times(2)).send(any());
        verify(notificationDeliveryRepository, times(2)).reserve(any(), any(), any());
    }

    // E. maxNotifications also bounds failed provider calls
    @Test
    void monitor_maxNotifications_alsoBoundsFailedProviderCalls() {
        Vacancy v1 = vacancy();
        Vacancy v2 = vacancy();
        JobOffer o1 = offer("1");
        JobOffer o2 = offer("2");
        when(vacancyIngestionService.ingest(any())).thenReturn(new VacancyIngestionResult(2, List.of(v1, v2), 0));
        when(candidateProfileProvider.getProfile()).thenReturn(profile);
        when(vacancyJobOfferMapper.toJobOffer(v1)).thenReturn(o1);
        when(vacancyJobOfferMapper.toJobOffer(v2)).thenReturn(o2);
        when(jobAnalysisService.analyze(profile, o1)).thenReturn(analysis(95));
        when(jobAnalysisService.analyze(profile, o2)).thenReturn(analysis(90));
        when(jobNotificationFactory.create(any(), any(), any())).thenAnswer(inv -> notification());
        when(notificationDeliveryRepository.reserve(any(), any(), any())).thenAnswer(
                inv -> NotificationReservationResult.reserved(pendingDelivery(inv.getArgument(0))));
        when(jobNotificationPort.send(any()))
                .thenThrow(new JobNotificationException(JobNotificationFailureType.PERMANENT_FAILURE, "nope"));
        when(notificationDeliveryRepository.markFailed(any(), any(), any()))
                .thenAnswer(inv -> NotificationDeliveryTransitionResult.updated(failedDelivery()));

        JobMonitoringResult result = service.monitor(command(50, 1));

        assertThat(result.notifiedCount()).isZero();
        assertThat(result.failedCount()).isEqualTo(1);
        verify(jobNotificationPort, times(1)).send(any());
        verify(notificationDeliveryRepository, times(1)).reserve(any(), any(), any());
    }

    // F. ALREADY_EXISTS reservation
    @Test
    void monitor_alreadyExistsReservation_skipsWithoutFailureAndWithoutConsumingAttemptSlot() {
        Vacancy v1 = vacancy();
        Vacancy v2 = vacancy();
        JobOffer o1 = offer("1");
        JobOffer o2 = offer("2");
        JobNotification n1 = notification();
        JobNotification n2 = notification();
        UUID deliveryId2 = UUID.randomUUID();
        NotificationDelivery pending2 = new NotificationDelivery(
                deliveryId2, v2.getId(), RECIPIENT_CHAT_ID, NotificationDeliveryStatus.PENDING, FIXED_INSTANT, null, null, null);

        when(vacancyIngestionService.ingest(any())).thenReturn(new VacancyIngestionResult(2, List.of(v1, v2), 0));
        when(candidateProfileProvider.getProfile()).thenReturn(profile);
        when(vacancyJobOfferMapper.toJobOffer(v1)).thenReturn(o1);
        when(vacancyJobOfferMapper.toJobOffer(v2)).thenReturn(o2);
        when(jobAnalysisService.analyze(profile, o1)).thenReturn(analysis(95));
        when(jobAnalysisService.analyze(profile, o2)).thenReturn(analysis(90));
        when(jobNotificationFactory.create(v1, analysis(95), RECIPIENT_CHAT_ID)).thenReturn(n1);
        when(jobNotificationFactory.create(v2, analysis(90), RECIPIENT_CHAT_ID)).thenReturn(n2);
        when(notificationDeliveryRepository.reserve(v1.getId(), RECIPIENT_CHAT_ID, FIXED_INSTANT))
                .thenReturn(NotificationReservationResult.alreadyExists());
        when(notificationDeliveryRepository.reserve(v2.getId(), RECIPIENT_CHAT_ID, FIXED_INSTANT))
                .thenReturn(NotificationReservationResult.reserved(pending2));
        when(jobNotificationPort.send(n2)).thenReturn(JobNotificationResult.accepted());
        when(notificationDeliveryRepository.markSent(deliveryId2, FIXED_INSTANT))
                .thenReturn(NotificationDeliveryTransitionResult.updated(sentDelivery()));

        JobMonitoringResult result = service.monitor(command(50, 1));

        assertThat(result.notifiedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();
        verify(jobNotificationPort, never()).send(n1);
        verify(jobNotificationPort, times(1)).send(n2);
    }

    // G. Successful send
    @Test
    void monitor_successfulSend_reservesSendsAndMarksTheSameDeliverySent() {
        Vacancy vacancy = vacancy();
        JobOffer offer = offer("1");
        JobAnalysis analysis = analysis(90);
        JobNotification notification = notification();
        UUID deliveryId = UUID.randomUUID();
        NotificationDelivery pending = new NotificationDelivery(
                deliveryId, vacancy.getId(), RECIPIENT_CHAT_ID, NotificationDeliveryStatus.PENDING, FIXED_INSTANT, null, null, null);
        NotificationDelivery sent = new NotificationDelivery(
                deliveryId, vacancy.getId(), RECIPIENT_CHAT_ID, NotificationDeliveryStatus.SENT, FIXED_INSTANT, FIXED_INSTANT, null, null);

        when(vacancyIngestionService.ingest(any())).thenReturn(new VacancyIngestionResult(1, List.of(vacancy), 0));
        when(candidateProfileProvider.getProfile()).thenReturn(profile);
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(offer);
        when(jobAnalysisService.analyze(profile, offer)).thenReturn(analysis);
        when(jobNotificationFactory.create(vacancy, analysis, RECIPIENT_CHAT_ID)).thenReturn(notification);
        when(notificationDeliveryRepository.reserve(vacancy.getId(), RECIPIENT_CHAT_ID, FIXED_INSTANT))
                .thenReturn(NotificationReservationResult.reserved(pending));
        when(jobNotificationPort.send(notification)).thenReturn(JobNotificationResult.accepted("msg-1"));
        when(notificationDeliveryRepository.markSent(deliveryId, FIXED_INSTANT))
                .thenReturn(NotificationDeliveryTransitionResult.updated(sent));

        JobMonitoringResult result = service.monitor(command(50, 5));

        assertThat(result.notifiedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();

        InOrder inOrder = inOrder(notificationDeliveryRepository, jobNotificationPort);
        inOrder.verify(notificationDeliveryRepository).reserve(vacancy.getId(), RECIPIENT_CHAT_ID, FIXED_INSTANT);
        inOrder.verify(jobNotificationPort).send(notification);
        inOrder.verify(notificationDeliveryRepository).markSent(deliveryId, FIXED_INSTANT);
    }

    // H. JobNotificationException
    @Test
    void monitor_jobNotificationException_marksFailedWithFailureTypeNameAndContinues() {
        Vacancy vacancy = vacancy();
        JobOffer offer = offer("1");
        JobAnalysis analysis = analysis(90);
        JobNotification notification = notification();
        UUID deliveryId = UUID.randomUUID();
        NotificationDelivery pending = new NotificationDelivery(
                deliveryId, vacancy.getId(), RECIPIENT_CHAT_ID, NotificationDeliveryStatus.PENDING, FIXED_INSTANT, null, null, null);
        NotificationDelivery failed = new NotificationDelivery(
                deliveryId, vacancy.getId(), RECIPIENT_CHAT_ID, NotificationDeliveryStatus.FAILED,
                FIXED_INSTANT, null, FIXED_INSTANT, "PERMANENT_FAILURE");

        when(vacancyIngestionService.ingest(any())).thenReturn(new VacancyIngestionResult(1, List.of(vacancy), 0));
        when(candidateProfileProvider.getProfile()).thenReturn(profile);
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(offer);
        when(jobAnalysisService.analyze(profile, offer)).thenReturn(analysis);
        when(jobNotificationFactory.create(vacancy, analysis, RECIPIENT_CHAT_ID)).thenReturn(notification);
        when(notificationDeliveryRepository.reserve(vacancy.getId(), RECIPIENT_CHAT_ID, FIXED_INSTANT))
                .thenReturn(NotificationReservationResult.reserved(pending));
        when(jobNotificationPort.send(notification))
                .thenThrow(new JobNotificationException(JobNotificationFailureType.PERMANENT_FAILURE, "cannot deliver"));
        when(notificationDeliveryRepository.markFailed(deliveryId, FIXED_INSTANT, "PERMANENT_FAILURE"))
                .thenReturn(NotificationDeliveryTransitionResult.updated(failed));

        JobMonitoringResult result = service.monitor(command(50, 5));

        assertThat(result.notifiedCount()).isZero();
        assertThat(result.failedCount()).isEqualTo(1);

        InOrder inOrder = inOrder(notificationDeliveryRepository, jobNotificationPort);
        inOrder.verify(notificationDeliveryRepository).reserve(vacancy.getId(), RECIPIENT_CHAT_ID, FIXED_INSTANT);
        inOrder.verify(jobNotificationPort).send(notification);
        inOrder.verify(notificationDeliveryRepository).markFailed(deliveryId, FIXED_INSTANT, "PERMANENT_FAILURE");
        verify(notificationDeliveryRepository, never()).markSent(any(), any());
    }

    // I. send failure plus markFailed failure -> failedCount increases only once
    @Test
    void monitor_sendFailureAndMarkFailedFailure_failedCountIncreasesOnlyOnce() {
        Vacancy vacancy = vacancy();
        JobOffer offer = offer("1");
        JobAnalysis analysis = analysis(90);
        JobNotification notification = notification();
        UUID deliveryId = UUID.randomUUID();
        NotificationDelivery pending = new NotificationDelivery(
                deliveryId, vacancy.getId(), RECIPIENT_CHAT_ID, NotificationDeliveryStatus.PENDING, FIXED_INSTANT, null, null, null);

        when(vacancyIngestionService.ingest(any())).thenReturn(new VacancyIngestionResult(1, List.of(vacancy), 0));
        when(candidateProfileProvider.getProfile()).thenReturn(profile);
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(offer);
        when(jobAnalysisService.analyze(profile, offer)).thenReturn(analysis);
        when(jobNotificationFactory.create(vacancy, analysis, RECIPIENT_CHAT_ID)).thenReturn(notification);
        when(notificationDeliveryRepository.reserve(vacancy.getId(), RECIPIENT_CHAT_ID, FIXED_INSTANT))
                .thenReturn(NotificationReservationResult.reserved(pending));
        when(jobNotificationPort.send(notification))
                .thenThrow(new JobNotificationException(JobNotificationFailureType.TEMPORARY_FAILURE, "provider down"));
        when(notificationDeliveryRepository.markFailed(any(), any(), any()))
                .thenThrow(new RuntimeException("db unavailable"));

        JobMonitoringResult result = service.monitor(command(50, 5));

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.notifiedCount()).isZero();
    }

    // J. provider accepts but markSent fails
    @Test
    void monitor_providerAcceptsButMarkSentFails_incrementsBothNotifiedAndFailedWithoutResendOrMarkFailed() {
        Vacancy vacancy = vacancy();
        JobOffer offer = offer("1");
        JobAnalysis analysis = analysis(90);
        JobNotification notification = notification();
        UUID deliveryId = UUID.randomUUID();
        NotificationDelivery pending = new NotificationDelivery(
                deliveryId, vacancy.getId(), RECIPIENT_CHAT_ID, NotificationDeliveryStatus.PENDING, FIXED_INSTANT, null, null, null);

        when(vacancyIngestionService.ingest(any())).thenReturn(new VacancyIngestionResult(1, List.of(vacancy), 0));
        when(candidateProfileProvider.getProfile()).thenReturn(profile);
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(offer);
        when(jobAnalysisService.analyze(profile, offer)).thenReturn(analysis);
        when(jobNotificationFactory.create(vacancy, analysis, RECIPIENT_CHAT_ID)).thenReturn(notification);
        when(notificationDeliveryRepository.reserve(vacancy.getId(), RECIPIENT_CHAT_ID, FIXED_INSTANT))
                .thenReturn(NotificationReservationResult.reserved(pending));
        when(jobNotificationPort.send(notification)).thenReturn(JobNotificationResult.accepted());
        when(notificationDeliveryRepository.markSent(deliveryId, FIXED_INSTANT))
                .thenReturn(NotificationDeliveryTransitionResult.notFound());

        JobMonitoringResult result = service.monitor(command(50, 5));

        assertThat(result.notifiedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        verify(jobNotificationPort, times(1)).send(notification);
        verify(notificationDeliveryRepository, never()).markFailed(any(), any(), any());
    }

    // K. analysis failure for one vacancy
    @Test
    void monitor_analysisFailureForOneVacancy_countsFailedOnceAndContinuesWithRemaining() {
        Vacancy failing = vacancy();
        Vacancy succeeding = vacancy();
        JobOffer failingOffer = offer("1");
        JobOffer succeedingOffer = offer("2");
        when(vacancyIngestionService.ingest(any())).thenReturn(new VacancyIngestionResult(2, List.of(failing, succeeding), 0));
        when(candidateProfileProvider.getProfile()).thenReturn(profile);
        when(vacancyJobOfferMapper.toJobOffer(failing)).thenReturn(failingOffer);
        when(vacancyJobOfferMapper.toJobOffer(succeeding)).thenReturn(succeedingOffer);
        when(jobAnalysisService.analyze(profile, failingOffer)).thenThrow(new JobAnalysisException("boom"));
        when(jobAnalysisService.analyze(profile, succeedingOffer)).thenReturn(analysis(20));

        JobMonitoringResult result = service.monitor(command(50, 5));

        assertThat(result.analyzedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.matchedCount()).isZero();
    }

    // L. reservation failure
    @Test
    void monitor_reservationFailure_countsFailedOnceAndDoesNotCallNotificationSender() {
        Vacancy vacancy = vacancy();
        JobOffer offer = offer("1");
        JobAnalysis analysis = analysis(90);
        JobNotification notification = notification();

        when(vacancyIngestionService.ingest(any())).thenReturn(new VacancyIngestionResult(1, List.of(vacancy), 0));
        when(candidateProfileProvider.getProfile()).thenReturn(profile);
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(offer);
        when(jobAnalysisService.analyze(profile, offer)).thenReturn(analysis);
        when(jobNotificationFactory.create(vacancy, analysis, RECIPIENT_CHAT_ID)).thenReturn(notification);
        when(notificationDeliveryRepository.reserve(vacancy.getId(), RECIPIENT_CHAT_ID, FIXED_INSTANT))
                .thenThrow(new RuntimeException("db error"));

        JobMonitoringResult result = service.monitor(command(50, 5));

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.notifiedCount()).isZero();
        verifyNoInteractions(jobNotificationPort);
    }

    // M. notification factory failure
    @Test
    void monitor_notificationFactoryFailure_countsFailedOnceWithNoReservationAndNoAttemptConsumed() {
        Vacancy failing = vacancy();
        Vacancy succeeding = vacancy();
        JobOffer failingOffer = offer("1");
        JobOffer succeedingOffer = offer("2");
        JobAnalysis highScore = analysis(95);
        JobAnalysis okScore = analysis(90);
        JobNotification notification = notification();
        UUID deliveryId = UUID.randomUUID();
        NotificationDelivery pending = new NotificationDelivery(
                deliveryId, succeeding.getId(), RECIPIENT_CHAT_ID, NotificationDeliveryStatus.PENDING, FIXED_INSTANT, null, null, null);
        NotificationDelivery sent = new NotificationDelivery(
                deliveryId, succeeding.getId(), RECIPIENT_CHAT_ID, NotificationDeliveryStatus.SENT, FIXED_INSTANT, FIXED_INSTANT, null, null);

        when(vacancyIngestionService.ingest(any())).thenReturn(new VacancyIngestionResult(2, List.of(failing, succeeding), 0));
        when(candidateProfileProvider.getProfile()).thenReturn(profile);
        when(vacancyJobOfferMapper.toJobOffer(failing)).thenReturn(failingOffer);
        when(vacancyJobOfferMapper.toJobOffer(succeeding)).thenReturn(succeedingOffer);
        when(jobAnalysisService.analyze(profile, failingOffer)).thenReturn(highScore);
        when(jobAnalysisService.analyze(profile, succeedingOffer)).thenReturn(okScore);
        when(jobNotificationFactory.create(failing, highScore, RECIPIENT_CHAT_ID))
                .thenThrow(new IllegalArgumentException("bad data"));
        when(jobNotificationFactory.create(succeeding, okScore, RECIPIENT_CHAT_ID)).thenReturn(notification);
        when(notificationDeliveryRepository.reserve(succeeding.getId(), RECIPIENT_CHAT_ID, FIXED_INSTANT))
                .thenReturn(NotificationReservationResult.reserved(pending));
        when(jobNotificationPort.send(notification)).thenReturn(JobNotificationResult.accepted());
        when(notificationDeliveryRepository.markSent(deliveryId, FIXED_INSTANT))
                .thenReturn(NotificationDeliveryTransitionResult.updated(sent));

        JobMonitoringResult result = service.monitor(command(50, 1));

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.notifiedCount()).isEqualTo(1);
        verify(notificationDeliveryRepository, never()).reserve(eq(failing.getId()), any(), any());
    }

    // N. unexpected runtime send failure
    @Test
    void monitor_unexpectedRuntimeSendFailure_marksFailedWithUnexpectedFailureCode() {
        Vacancy vacancy = vacancy();
        JobOffer offer = offer("1");
        JobAnalysis analysis = analysis(90);
        JobNotification notification = notification();
        UUID deliveryId = UUID.randomUUID();
        NotificationDelivery pending = new NotificationDelivery(
                deliveryId, vacancy.getId(), RECIPIENT_CHAT_ID, NotificationDeliveryStatus.PENDING, FIXED_INSTANT, null, null, null);

        when(vacancyIngestionService.ingest(any())).thenReturn(new VacancyIngestionResult(1, List.of(vacancy), 0));
        when(candidateProfileProvider.getProfile()).thenReturn(profile);
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(offer);
        when(jobAnalysisService.analyze(profile, offer)).thenReturn(analysis);
        when(jobNotificationFactory.create(vacancy, analysis, RECIPIENT_CHAT_ID)).thenReturn(notification);
        when(notificationDeliveryRepository.reserve(vacancy.getId(), RECIPIENT_CHAT_ID, FIXED_INSTANT))
                .thenReturn(NotificationReservationResult.reserved(pending));
        when(jobNotificationPort.send(notification)).thenThrow(new RuntimeException("unexpected failure"));
        when(notificationDeliveryRepository.markFailed(deliveryId, FIXED_INSTANT, JobNotificationFailureType.UNEXPECTED_FAILURE.name()))
                .thenReturn(NotificationDeliveryTransitionResult.updated(failedDelivery()));

        JobMonitoringResult result = service.monitor(command(50, 5));

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.notifiedCount()).isZero();
        verify(notificationDeliveryRepository)
                .markFailed(deliveryId, FIXED_INSTANT, JobNotificationFailureType.UNEXPECTED_FAILURE.name());
    }

    // O. candidate profile loaded once per non-empty run
    @Test
    void monitor_candidateProfileLoadedOncePerNonEmptyRun() {
        Vacancy v1 = vacancy();
        Vacancy v2 = vacancy();
        when(vacancyIngestionService.ingest(any())).thenReturn(new VacancyIngestionResult(2, List.of(v1, v2), 0));
        when(candidateProfileProvider.getProfile()).thenReturn(profile);
        when(vacancyJobOfferMapper.toJobOffer(any())).thenReturn(offer("x"));
        when(jobAnalysisService.analyze(any(), any())).thenReturn(analysis(10));

        service.monitor(command(50, 5));

        verify(candidateProfileProvider, times(1)).getProfile();
    }

    @Test
    void monitor_profileLoadingFails_throwsJobMonitoringExceptionAndSkipsDownstream() {
        Vacancy vacancy = vacancy();
        when(vacancyIngestionService.ingest(any())).thenReturn(new VacancyIngestionResult(1, List.of(vacancy), 0));
        when(candidateProfileProvider.getProfile()).thenThrow(new IllegalStateException("profile config missing"));

        assertThatThrownBy(() -> service.monitor(command(50, 5)))
                .isInstanceOf(JobMonitoringException.class);

        verifyNoInteractions(jobAnalysisService, jobNotificationFactory, jobNotificationPort, notificationDeliveryRepository);
    }

    private void stubGenericSuccessfulSend() {
        when(jobNotificationFactory.create(any(), any(), any())).thenAnswer(inv -> notification());
        when(notificationDeliveryRepository.reserve(any(), any(), any()))
                .thenAnswer(inv -> NotificationReservationResult.reserved(pendingDelivery(inv.getArgument(0))));
        when(jobNotificationPort.send(any())).thenReturn(JobNotificationResult.accepted());
        when(notificationDeliveryRepository.markSent(any(), any()))
                .thenAnswer(inv -> NotificationDeliveryTransitionResult.updated(sentDelivery()));
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
        return new JobAnalysis(score, List.of(), List.of(), List.of(), "Great match");
    }

    private JobNotification notification() {
        return new JobNotification(
                UUID.randomUUID(), RECIPIENT_CHAT_ID, "Backend Engineer", "Acme", "https://example.com/job",
                90, "Great match", List.of(), List.of(), List.of());
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
