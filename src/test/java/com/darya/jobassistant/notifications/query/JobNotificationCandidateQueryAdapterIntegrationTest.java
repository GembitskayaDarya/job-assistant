package com.darya.jobassistant.notifications.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.ai.entity.AnalysisStatus;
import com.darya.jobassistant.ai.entity.JobAnalysisEntity;
import com.darya.jobassistant.ai.repository.JobAnalysisRepository;
import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.repository.CompanyRepository;
import com.darya.jobassistant.config.JpaAuditingConfig;
import com.darya.jobassistant.notifications.repository.NotificationDeliveryRepository;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Column-name coupling note: {@link JobNotificationCandidateQueryAdapter} hydrates candidates by
 * re-querying {@link VacancyRepository} / {@link JobAnalysisRepository} for the ids its native
 * query selects, rather than mapping a multi-table native row by column position - so there is no
 * column-order coupling to document here beyond the native query's own single {@code v.id}
 * projection.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({JpaAuditingConfig.class, JobNotificationCandidateQueryAdapter.class})
class JobNotificationCandidateQueryAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Long RECIPIENT = 1000L;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private VacancyRepository vacancyRepository;

    @Autowired
    private JobAnalysisRepository jobAnalysisRepository;

    @Autowired
    private NotificationDeliveryRepository notificationDeliveryRepository;

    @Autowired
    private JobNotificationCandidateQueryPort candidateQueryPort;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void matchingAnalyzedVacancyWithoutDelivery_isReturned() {
        Vacancy vacancy = newVacancy();
        analyze(vacancy.getId(), 80);

        List<JobNotificationCandidate> candidates = candidateQueryPort.findCandidates(RECIPIENT, 60, 10);

        assertThat(candidates).extracting(c -> c.vacancy().getId()).containsExactly(vacancy.getId());
    }

    @Test
    void analysisBelowMinimumScore_isNotReturned() {
        Vacancy vacancy = newVacancy();
        analyze(vacancy.getId(), 40);

        List<JobNotificationCandidate> candidates = candidateQueryPort.findCandidates(RECIPIENT, 60, 10);

        assertThat(candidates).isEmpty();
    }

    @Test
    void vacancyWithoutPersistedAnalysis_isNotReturned() {
        newVacancy();

        List<JobNotificationCandidate> candidates = candidateQueryPort.findCandidates(RECIPIENT, 0, 10);

        assertThat(candidates).isEmpty();
    }

    @Test
    void existingPendingDelivery_excludesCandidate() {
        Vacancy vacancy = newVacancy();
        analyze(vacancy.getId(), 80);
        notificationDeliveryRepository.reserve(vacancy.getId(), RECIPIENT, Instant.now());

        List<JobNotificationCandidate> candidates = candidateQueryPort.findCandidates(RECIPIENT, 60, 10);

        assertThat(candidates).isEmpty();
    }

    @Test
    void existingSentDelivery_excludesCandidate() {
        Vacancy vacancy = newVacancy();
        analyze(vacancy.getId(), 80);
        UUID deliveryId = notificationDeliveryRepository.reserve(vacancy.getId(), RECIPIENT, Instant.now()).delivery().id();
        notificationDeliveryRepository.markSent(deliveryId, Instant.now());

        List<JobNotificationCandidate> candidates = candidateQueryPort.findCandidates(RECIPIENT, 60, 10);

        assertThat(candidates).isEmpty();
    }

    @Test
    void existingFailedDelivery_excludesCandidate() {
        Vacancy vacancy = newVacancy();
        analyze(vacancy.getId(), 80);
        UUID deliveryId = notificationDeliveryRepository.reserve(vacancy.getId(), RECIPIENT, Instant.now()).delivery().id();
        notificationDeliveryRepository.markFailed(deliveryId, Instant.now(), "RATE_LIMITED");

        List<JobNotificationCandidate> candidates = candidateQueryPort.findCandidates(RECIPIENT, 60, 10);

        assertThat(candidates).isEmpty();
    }

    @Test
    void deliveryForAnotherRecipient_leavesVacancyEligibleForRequestedRecipient() {
        Vacancy vacancy = newVacancy();
        analyze(vacancy.getId(), 80);
        Long otherRecipient = 2000L;
        notificationDeliveryRepository.reserve(vacancy.getId(), otherRecipient, Instant.now());

        List<JobNotificationCandidate> candidates = candidateQueryPort.findCandidates(RECIPIENT, 60, 10);

        assertThat(candidates).extracting(c -> c.vacancy().getId()).containsExactly(vacancy.getId());
    }

    @Test
    void previouslyPersistedAnalyzedVacancy_isReturnedEvenThoughNotCreatedDuringThisQueryCall() {
        // Nothing here is created "during" findCandidates - the query has no notion of a current
        // ingestion run or batch; it only reads whatever is already durably persisted. Backdating
        // the analysis timestamp makes that independence from "recency" explicit.
        Vacancy vacancy = newVacancy();
        JobAnalysisEntity analysis = analyze(vacancy.getId(), 80);
        backdateAnalysisCreatedAt(analysis.getId(), Instant.now().minus(30, ChronoUnit.DAYS));

        List<JobNotificationCandidate> candidates = candidateQueryPort.findCandidates(RECIPIENT, 60, 10);

        assertThat(candidates).extracting(c -> c.vacancy().getId()).containsExactly(vacancy.getId());
    }

    @Test
    void ordersByScoreDescendingThenByAnalysisAgeAscendingForEqualScores() {
        Instant base = Instant.now().minus(1, ChronoUnit.HOURS);
        Vacancy lowScore = newVacancy();
        JobAnalysisEntity lowScoreAnalysis = analyze(lowScore.getId(), 70);
        backdateAnalysisCreatedAt(lowScoreAnalysis.getId(), base.plusSeconds(20));

        Vacancy olderHighScore = newVacancy();
        JobAnalysisEntity olderHighScoreAnalysis = analyze(olderHighScore.getId(), 90);
        backdateAnalysisCreatedAt(olderHighScoreAnalysis.getId(), base);

        Vacancy newerHighScore = newVacancy();
        JobAnalysisEntity newerHighScoreAnalysis = analyze(newerHighScore.getId(), 90);
        backdateAnalysisCreatedAt(newerHighScoreAnalysis.getId(), base.plusSeconds(10));

        List<JobNotificationCandidate> candidates = candidateQueryPort.findCandidates(RECIPIENT, 0, 10);

        assertThat(candidates).extracting(c -> c.vacancy().getId())
                .containsExactly(olderHighScore.getId(), newerHighScore.getId(), lowScore.getId());
    }

    @Test
    void tieBreaksByVacancyIdAscendingWhenScoreAndAnalysisAgeAreEqual() {
        Instant sameInstant = Instant.now().minus(1, ChronoUnit.HOURS);
        Vacancy vacancyA = newVacancy();
        Vacancy vacancyB = newVacancy();
        JobAnalysisEntity analysisA = analyze(vacancyA.getId(), 80);
        JobAnalysisEntity analysisB = analyze(vacancyB.getId(), 80);
        backdateAnalysisCreatedAt(analysisA.getId(), sameInstant);
        backdateAnalysisCreatedAt(analysisB.getId(), sameInstant);

        List<JobNotificationCandidate> candidates = candidateQueryPort.findCandidates(RECIPIENT, 0, 10);

        // Postgres orders uuid columns by raw byte value, not by java.util.UUID#compareTo's
        // signed-long comparison of the two 64-bit halves - those two orderings diverge for
        // random UUIDs whenever the top bit differs, so the expectation must be sorted the same
        // way the database sorts it (canonical string form == byte order for this format).
        List<UUID> expectedOrder = List.of(vacancyA.getId(), vacancyB.getId()).stream()
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
        assertThat(candidates).extracting(c -> c.vacancy().getId()).containsExactlyElementsOf(expectedOrder);
    }

    @Test
    void limitBoundsTheNumberOfCandidatesReturned() {
        for (int i = 0; i < 5; i++) {
            Vacancy vacancy = newVacancy();
            analyze(vacancy.getId(), 80);
        }

        List<JobNotificationCandidate> candidates = candidateQueryPort.findCandidates(RECIPIENT, 0, 3);

        assertThat(candidates).hasSize(3);
    }

    @Test
    void secondAnalysisForSameVacancy_violatesUniqueConstraint() {
        // job_analysis.vacancy_id is UNIQUE, so at most one analysis can ever exist per vacancy -
        // that row is always the authoritative one. This documents that invariant: a second
        // insert for the same vacancy is rejected at the database level, so the "multiple
        // analysis rows" backlog scenario does not apply to this schema.
        Vacancy vacancy = newVacancy();
        analyze(vacancy.getId(), 80);

        assertThatThrownBy(() -> {
            jobAnalysisRepository.saveAndFlush(JobAnalysisEntity.builder()
                    .vacancyId(vacancy.getId())
                    .status(AnalysisStatus.COMPLETED)
                    .score(90)
                    .summary("Another analysis")
                    .pros(List.of())
                    .cons(List.of())
                    .missingSkills(List.of())
                    .build());
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void candidateMapping_containsAllFieldsRequiredByJobNotificationFactory() {
        Company company = companyRepository.save(Company.builder().name("Acme Corp").build());
        Vacancy vacancy = vacancyRepository.save(Vacancy.builder()
                .company(company)
                .title("Senior Backend Engineer")
                .description("Build backend services")
                .url("https://example.com/job-" + UUID.randomUUID())
                .salaryMin(BigDecimal.valueOf(100_000))
                .salaryMax(BigDecimal.valueOf(140_000))
                .currency("USD")
                .source("remoteok")
                .build());
        jobAnalysisRepository.save(JobAnalysisEntity.builder()
                .vacancyId(vacancy.getId())
                .status(AnalysisStatus.COMPLETED)
                .score(88)
                .summary("Great match for the role")
                .pros(List.of("Strong Java skills", "Kafka experience"))
                .cons(List.of("No AWS mentioned"))
                .missingSkills(List.of("AWS"))
                .build());

        List<JobNotificationCandidate> candidates = candidateQueryPort.findCandidates(RECIPIENT, 0, 10);

        assertThat(candidates).hasSize(1);
        JobNotificationCandidate candidate = candidates.get(0);
        assertThat(candidate.vacancy().getId()).isEqualTo(vacancy.getId());
        assertThat(candidate.vacancy().getTitle()).isEqualTo("Senior Backend Engineer");
        assertThat(candidate.vacancy().getUrl()).isEqualTo(vacancy.getUrl());
        assertThat(candidate.vacancy().getCompany().getName()).isEqualTo("Acme Corp");
        assertThat(candidate.analysis().vacancyId()).isEqualTo(vacancy.getId());
        assertThat(candidate.analysis().analysis().score()).isEqualTo(88);
        assertThat(candidate.analysis().analysis().summary()).isEqualTo("Great match for the role");
        assertThat(candidate.analysis().analysis().pros()).containsExactly("Strong Java skills", "Kafka experience");
        assertThat(candidate.analysis().analysis().cons()).containsExactly("No AWS mentioned");
        assertThat(candidate.analysis().analysis().missingSkills()).containsExactly("AWS");
    }

    @Test
    void nullRecipientChatId_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> candidateQueryPort.findCandidates(null, 50, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scoreOutOfRange_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> candidateQueryPort.findCandidates(RECIPIENT, 150, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonPositiveLimit_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> candidateQueryPort.findCandidates(RECIPIENT, 50, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Vacancy newVacancy() {
        Company company = companyRepository.save(Company.builder().name("Acme " + UUID.randomUUID()).build());
        return vacancyRepository.save(Vacancy.builder()
                .company(company)
                .title("Backend Engineer")
                .url("https://example.com/job-" + UUID.randomUUID())
                .source("remoteok")
                .build());
    }

    private JobAnalysisEntity analyze(UUID vacancyId, int score) {
        return jobAnalysisRepository.save(JobAnalysisEntity.builder()
                .vacancyId(vacancyId)
                .status(AnalysisStatus.COMPLETED)
                .score(score)
                .summary("Summary")
                .pros(List.of("Java"))
                .cons(List.of())
                .missingSkills(List.of())
                .build());
    }

    private void backdateAnalysisCreatedAt(UUID analysisId, Instant createdAt) {
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE job_analysis SET created_at = :createdAt WHERE id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", analysisId)
                .executeUpdate();
        entityManager.clear();
    }
}
