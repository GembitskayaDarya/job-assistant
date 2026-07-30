package com.darya.jobassistant.vacancyrecommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.ai.entity.AnalysisStatus;
import com.darya.jobassistant.ai.entity.JobAnalysisEntity;
import com.darya.jobassistant.ai.model.AnalysisOrigin;
import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.ai.repository.JobAnalysisRepository;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateProfileProvider;
import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.repository.CompanyRepository;
import com.darya.jobassistant.config.JpaAuditingConfig;
import com.darya.jobassistant.integrations.ai.openai.JobAnalysisService;
import com.darya.jobassistant.integrations.notifier.JobNotificationFactory;
import com.darya.jobassistant.integrations.notifier.JobNotificationPort;
import com.darya.jobassistant.integrations.notifier.JobNotificationResult;
import com.darya.jobassistant.notifications.entity.NotificationDeliveryStatus;
import com.darya.jobassistant.notifications.repository.NotificationDeliveryRepository;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.mapper.VacancyJobOfferMapper;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import com.darya.jobassistant.vacancyrecommendation.config.RecommendationPolicyProperties;
import com.darya.jobassistant.vacancyrecommendation.config.VacancyRecommendationProperties;
import com.darya.jobassistant.vacancyrecommendation.entity.VacancyRecommendationTaskEntity;
import com.darya.jobassistant.vacancyrecommendation.repository.VacancyRecommendationTaskRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Real-PostgreSQL durability/restart proof for {@link VacancyRecommendationProcessingService}:
 * every scenario here constructs a *fresh* service instance against already-committed database
 * state, simulating a process restart (crash, redeploy, or a second node) rather than reusing one
 * long-lived in-memory instance. Only {@link JobAnalysisService} (OpenAI) and {@link
 * JobNotificationPort} (Telegram) are mocked - every persistence collaborator is the real,
 * Testcontainers-backed repository, so this proves actual committed rows survive and drive
 * correct behavior across a restart, not just in-memory state.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(JpaAuditingConfig.class)
class VacancyRecommendationProcessingServiceDurabilityIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Instant NOW = Instant.parse("2026-07-30T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Long RECIPIENT_CHAT_ID = 555L;
    private static final int MINIMUM_SCORE = 70;

    @Autowired
    private VacancyRecommendationTaskRepository taskRepository;

    @Autowired
    private JobAnalysisRepository jobAnalysisRepository;

    @Autowired
    private NotificationDeliveryRepository notificationDeliveryRepository;

    @Autowired
    private VacancyRepository vacancyRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        // Every test in this class runs its Vacancy/task/analysis/delivery setup - and the
        // service call itself - via real, separately-committing transactions (see
        // VacancyCanonicalUrlBackfillRepositoryTest's javadoc for why: this class's own
        // ambient @DataJpaTest transaction would otherwise suspend and hide that data from
        // VacancyRecommendationProcessingService's REQUIRES_NEW transactions). That means
        // nothing here is rolled back automatically, so it must be cleaned up explicitly.
        requiresNewTransaction().executeWithoutResult(status -> {
            entityManager.createNativeQuery("DELETE FROM vacancy_recommendation_task").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM notification_delivery").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM job_analysis").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM vacancy").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM company").executeUpdate();
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void abandonedLeaseFromCrashedNode_isRecoveredAndCompletedByAFreshServiceInstance() {
        UUID taskId = requiresNewTransaction().execute(status -> {
            UUID vacancyId = persistVacancy().getId();
            persistManuallyReviewedAnalysis(vacancyId); // shortest path to a terminal outcome
            return persistTask(vacancyId, VacancyRecommendationTaskStatus.PROCESSING,
                    Instant.now().minusSeconds(1), "crashed-node", 1);
        });

        VacancyRecommendationProcessingService freshInstance = newService(mock(JobAnalysisService.class), mock(JobNotificationPort.class));
        VacancyRecommendationProcessingResult result = freshInstance.processPending();

        assertThat(result.leaseRecoveredTasks()).isEqualTo(1);
        assertThat(result.manuallyReviewedTasks()).isEqualTo(1);
        VacancyRecommendationTaskEntity reloaded = taskRepository.findById(taskId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(VacancyRecommendationTaskStatus.COMPLETED);
        assertThat(reloaded.getOutcome()).isEqualTo(VacancyRecommendationTaskOutcome.MANUALLY_REVIEWED);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void persistedPendingTask_fromBeforeARestart_isProcessedEndToEndByANewNode() {
        UUID vacancyId = requiresNewTransaction().execute(status -> persistVacancy().getId());
        UUID taskId = requiresNewTransaction().execute(status ->
                persistTask(vacancyId, VacancyRecommendationTaskStatus.PENDING, Instant.now(), null, 0));

        JobAnalysisService jobAnalysisService = mock(JobAnalysisService.class);
        when(jobAnalysisService.analyze(any(), any())).thenReturn(analysis(90));
        JobNotificationPort jobNotificationPort = mock(JobNotificationPort.class);
        when(jobNotificationPort.send(any())).thenReturn(JobNotificationResult.accepted("msg-1"));

        VacancyRecommendationProcessingService newNode = newService(jobAnalysisService, jobNotificationPort);
        VacancyRecommendationProcessingResult result = newNode.processPending();

        assertThat(result.notifiedTasks()).isEqualTo(1);
        VacancyRecommendationTaskEntity reloaded = taskRepository.findById(taskId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(VacancyRecommendationTaskStatus.COMPLETED);
        assertThat(reloaded.getOutcome()).isEqualTo(VacancyRecommendationTaskOutcome.NOTIFIED);
        assertThat(jobAnalysisRepository.findByVacancyId(vacancyId)).isPresent();
        assertThat(jobAnalysisRepository.findByVacancyId(vacancyId).orElseThrow().getAnalysisOrigin())
                .isEqualTo(AnalysisOrigin.AUTOMATIC_DISCOVERY);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void manualReviewCommittedBeforeRestart_stillSuppressesAutomaticProcessingAfterRestart() {
        UUID taskId = requiresNewTransaction().execute(status -> {
            UUID vacancyId = persistVacancy().getId();
            persistManuallyReviewedAnalysis(vacancyId);
            return persistTask(vacancyId, VacancyRecommendationTaskStatus.PENDING, Instant.now(), null, 0);
        });

        JobAnalysisService jobAnalysisService = mock(JobAnalysisService.class);
        JobNotificationPort jobNotificationPort = mock(JobNotificationPort.class);
        VacancyRecommendationProcessingService newNode = newService(jobAnalysisService, jobNotificationPort);

        VacancyRecommendationProcessingResult result = newNode.processPending();

        assertThat(result.manuallyReviewedTasks()).isEqualTo(1);
        VacancyRecommendationTaskEntity reloaded = taskRepository.findById(taskId).orElseThrow();
        assertThat(reloaded.getOutcome()).isEqualTo(VacancyRecommendationTaskOutcome.MANUALLY_REVIEWED);
        verify(jobAnalysisService, never()).analyze(any(), any());
        verify(jobNotificationPort, never()).send(any());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void sentNotificationDelivery_survivesRestart_taskCompletesAsAlreadyNotifiedWithoutResending() {
        UUID vacancyId = requiresNewTransaction().execute(status -> persistVacancy().getId());
        UUID taskId = requiresNewTransaction().execute(status -> {
            persistAutomaticDiscoveryAnalysis(vacancyId, 90);
            persistSentDelivery(vacancyId);
            return persistTask(vacancyId, VacancyRecommendationTaskStatus.RETRY_WAIT, Instant.now().minusSeconds(1), null, 1);
        });

        JobAnalysisService jobAnalysisService = mock(JobAnalysisService.class);
        JobNotificationPort jobNotificationPort = mock(JobNotificationPort.class);
        VacancyRecommendationProcessingService newNode = newService(jobAnalysisService, jobNotificationPort);

        VacancyRecommendationProcessingResult result = newNode.processPending();

        assertThat(result.alreadyNotifiedTasks()).isEqualTo(1);
        VacancyRecommendationTaskEntity reloaded = taskRepository.findById(taskId).orElseThrow();
        assertThat(reloaded.getOutcome()).isEqualTo(VacancyRecommendationTaskOutcome.ALREADY_NOTIFIED);
        verify(jobNotificationPort, never()).send(any());
        assertThat(notificationDeliveryRepository.findExistingDelivery(vacancyId, RECIPIENT_CHAT_ID).orElseThrow().status())
                .isEqualTo(NotificationDeliveryStatus.SENT);
    }

    /**
     * The end-to-end counterpart of {@code VacancyRecommendationTaskRepositoryConcurrencyTest}'s
     * repository-level claim race: two independent {@link VacancyRecommendationProcessingService}
     * instances (each its own fresh "node") race a normal run over the same single eligible task.
     * Exactly one Telegram send must happen in total, never two, even though both nodes call
     * {@code processPending()} concurrently.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void twoProcessingNodes_concurrentNormalRun_sendsExactlyOneNotificationNoDuplicates() throws Exception {
        UUID vacancyId = requiresNewTransaction().execute(status -> {
            UUID id = persistVacancy().getId();
            persistTask(id, VacancyRecommendationTaskStatus.PENDING, Instant.now(), null, 0);
            persistAutomaticDiscoveryAnalysis(id, 90);
            return id;
        });

        JobNotificationPort sharedPort = mock(JobNotificationPort.class);
        when(sharedPort.send(any())).thenReturn(JobNotificationResult.accepted("msg-1"));
        VacancyRecommendationProcessingService nodeA = newService(mock(JobAnalysisService.class), sharedPort);
        VacancyRecommendationProcessingService nodeB = newService(mock(JobAnalysisService.class), sharedPort);

        CyclicBarrier barrier = new CyclicBarrier(2);
        Future<VacancyRecommendationProcessingResult> futureA = executor.submit(() -> {
            barrier.await();
            return nodeA.processPending();
        });
        Future<VacancyRecommendationProcessingResult> futureB = executor.submit(() -> {
            barrier.await();
            return nodeB.processPending();
        });

        VacancyRecommendationProcessingResult resultA = futureA.get(10, TimeUnit.SECONDS);
        VacancyRecommendationProcessingResult resultB = futureB.get(10, TimeUnit.SECONDS);

        assertThat(resultA.notifiedTasks() + resultB.notifiedTasks()).isEqualTo(1);
        verify(sharedPort, times(1)).send(any());
        VacancyRecommendationTaskEntity reloaded = taskRepository.findByVacancyId(vacancyId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(VacancyRecommendationTaskStatus.COMPLETED);
        assertThat(reloaded.getOutcome()).isEqualTo(VacancyRecommendationTaskOutcome.NOTIFIED);
        // Cleanup for this NOT_SUPPORTED test's committed rows happens in the shared @AfterEach.
    }

    // --- Fixtures -------------------------------------------------------------------------------

    private VacancyRecommendationProcessingService newService(JobAnalysisService jobAnalysisService, JobNotificationPort jobNotificationPort) {
        RecommendationPolicyProperties policyProperties = new RecommendationPolicyProperties(MINIMUM_SCORE);
        VacancyRecommendationProperties properties = new VacancyRecommendationProperties(
                true, RECIPIENT_CHAT_ID,
                new VacancyRecommendationProperties.Processing(
                        5, 3, Duration.ofMinutes(20), Duration.ofMinutes(10), Duration.ofHours(2)),
                new VacancyRecommendationProperties.Scheduler(
                        false, Duration.ofMinutes(10), Duration.ofMinutes(1), Duration.ofHours(1), Duration.ofSeconds(10)));
        CandidateProfileProvider candidateProfileProvider = mock(CandidateProfileProvider.class);
        lenient().when(candidateProfileProvider.getProfile()).thenReturn(mock(CandidateProfile.class));
        return new VacancyRecommendationProcessingService(
                taskRepository, vacancyRepository, jobAnalysisRepository, new VacancyJobOfferMapper(),
                candidateProfileProvider, jobAnalysisService, new JobNotificationFactory(), jobNotificationPort,
                notificationDeliveryRepository, policyProperties, properties, CLOCK, transactionManager);
    }

    private JobAnalysis analysis(int score) {
        return new JobAnalysis(score, List.of("Java"), List.of(), List.of(), List.of(),
                "6 years vs. no stated requirement.", "Remote preference matches.", "Solid match");
    }

    private TransactionTemplate requiresNewTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private void persistManuallyReviewedAnalysis(UUID vacancyId) {
        jobAnalysisRepository.save(JobAnalysisEntity.builder()
                .vacancyId(vacancyId)
                .status(AnalysisStatus.COMPLETED)
                .score(90)
                .summary("Solid match")
                .pros(List.of()).cons(List.of())
                .missingRequiredSkills(List.of()).missingPreferredSkills(List.of())
                .experienceAssessment("Meets requirement")
                .preferencesAssessment("Remote preference matches")
                .analysisVersion(1)
                .analysisOrigin(AnalysisOrigin.MANUAL)
                .manuallyReviewedAt(NOW)
                .build());
        entityManager.flush();
        entityManager.clear();
    }

    private void persistAutomaticDiscoveryAnalysis(UUID vacancyId, int score) {
        jobAnalysisRepository.save(JobAnalysisEntity.builder()
                .vacancyId(vacancyId)
                .status(AnalysisStatus.COMPLETED)
                .score(score)
                .summary("Solid match")
                .pros(List.of()).cons(List.of())
                .missingRequiredSkills(List.of()).missingPreferredSkills(List.of())
                .experienceAssessment("Meets requirement")
                .preferencesAssessment("Remote preference matches")
                .analysisVersion(1)
                .analysisOrigin(AnalysisOrigin.AUTOMATIC_DISCOVERY)
                .build());
        entityManager.flush();
        entityManager.clear();
    }

    private void persistSentDelivery(UUID vacancyId) {
        entityManager.createNativeQuery("""
                INSERT INTO notification_delivery (id, vacancy_id, recipient_chat_id, status, created_at, updated_at, sent_at)
                VALUES (?1, ?2, ?3, 'SENT', now(), now(), now())
                """)
                .setParameter(1, UUID.randomUUID())
                .setParameter(2, vacancyId)
                .setParameter(3, RECIPIENT_CHAT_ID)
                .executeUpdate();
        entityManager.clear();
    }

    private UUID persistTask(UUID vacancyId, VacancyRecommendationTaskStatus status, Instant nextAttemptAt,
                              String leaseOwner, int attemptCount) {
        VacancyRecommendationTaskEntity.VacancyRecommendationTaskEntityBuilder<?, ?> builder =
                VacancyRecommendationTaskEntity.builder()
                        .vacancyId(vacancyId)
                        .status(status)
                        .attemptCount(attemptCount)
                        .nextAttemptAt(nextAttemptAt);
        if (status == VacancyRecommendationTaskStatus.PROCESSING) {
            builder.leaseUntil(Instant.now().minusSeconds(1)).leaseOwner(leaseOwner);
        }
        VacancyRecommendationTaskEntity task = taskRepository.save(builder.build());
        entityManager.flush();
        entityManager.clear();
        return task.getId();
    }

    private Vacancy persistVacancy() {
        Company company = companyRepository.save(Company.builder().name("Acme " + UUID.randomUUID()).build());
        String url = "https://example.com/job/" + UUID.randomUUID();
        Vacancy vacancy = vacancyRepository.save(Vacancy.builder()
                .company(company)
                .title("Backend Engineer")
                .url(url)
                .canonicalUrl(url)
                .source("remoteok")
                .build());
        entityManager.flush();
        return vacancy;
    }
}
