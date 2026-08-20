package com.darya.jobassistant.applicationmaterials.preparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.AbstractIntegrationTest;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGeneration;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationRepositoryPort;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationStatus;
import com.darya.jobassistant.applicationmaterials.generation.model.ApplicationMaterialsAiPort;
import com.darya.jobassistant.applicationmaterials.generation.model.ApplicationMaterialsGenerationFailureCode;
import com.darya.jobassistant.applicationmaterials.generation.model.ApplicationMaterialsGenerationResponse;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCoverLetter;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCoverLetterParagraph;
import com.darya.jobassistant.candidatecontext.CandidateContextProvider;
import com.darya.jobassistant.candidatecontext.CandidateContextSnapshot;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvTailoringResult;
import com.darya.jobassistant.candidatecontext.cv.tailoring.ai.CvTailoringAiPort;
import com.darya.jobassistant.careerhistory.aggregate.CareerCompany;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryRepositoryPort;
import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.repository.CompanyRepository;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Sprint 10 Step 5 (production-readiness acceptance fix): proves the AI-cost invariant under
 * genuine concurrency, end to end, against real Spring-wired beans, real PostgreSQL, real PDF
 * rendering, and a real (temporary) local filesystem root - mirrors {@code
 * CareerHistoryImportUseCaseConcurrencyTest}/{@code RenderApplicationMaterialsUseCaseIntegrationTest}'s
 * "real threads racing against real infrastructure" technique, not merely sequential calls.
 *
 * <p>Two threads call {@link PrepareApplicationPackageUseCase#prepare} for the exact same Vacancy
 * and the exact same current candidate context at (as close to) the same instant, synchronized by a
 * {@link CyclicBarrier}. Whichever thread's insert loses V25's {@code uk_amg_active_effective_key}
 * race joins the winner's row rather than creating a second one - see {@code
 * PrepareApplicationPackageUseCase}'s javadoc - so {@link ApplicationMaterialsAiPort#generate} must
 * be invoked at most once regardless of which thread actually wins, and neither thread may observe
 * an unhandled exception.
 */
/**
 * Ordered deterministically ({@link Order}), not alphabetically/by-hash: this is a full {@code
 * @SpringBootTest} against one shared, never-rolled-back Testcontainers PostgreSQL instance for the
 * whole class (unlike {@code @DataJpaTest}, there is no per-test transaction rollback here), and
 * Career History is a singleton per candidate profile - once any test adds it via {@link
 * #ensureCareerHistoryExists()} it stays present for every later test in this class. Every test that
 * asserts a specific "no Career History yet" precondition must therefore run before the first test
 * that creates one.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PrepareApplicationPackageUseCaseConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @TempDir
    static Path tempStorageRoot;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("application-materials.storage.local.root-directory", () -> tempStorageRoot.toString());
    }

    @Autowired
    private PrepareApplicationPackageUseCase useCase;

    @Autowired
    private ApplicationMaterialGenerationRepositoryPort generationRepositoryPort;

    @Autowired
    private CandidateContextProvider candidateContextProvider;

    @Autowired
    private CareerHistoryRepositoryPort careerHistoryRepositoryPort;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private VacancyRepository vacancyRepository;

    @MockitoBean
    private ApplicationMaterialsAiPort aiPort;

    @MockitoBean
    private CvTailoringAiPort cvTailoringAiPort;

    @BeforeEach
    void stubCvTailoringToSucceedByDefault() {
        when(cvTailoringAiPort.tailor(any(), any())).thenReturn(new CvTailoringResult(null, List.of(), List.of(), List.of()));
    }

    @Test
    @Order(1)
    void concurrentFirstTimeRequests_nullCareerHistoryVersion_atMostOneAiGenerationCallWins() throws Exception {
        // The seeded "primary" test candidate profile (see AbstractIntegrationTest) has no Career
        // History imported, so the current effective careerHistoryVersion is null - exactly the
        // case NULLS NOT DISTINCT (V25) exists to cover.
        CandidateContextSnapshot snapshot = candidateContextProvider.loadCurrentContext();
        assertThat(snapshot.careerHistory()).as("this test requires no Career History for the seeded profile").isEmpty();

        UUID vacancyId = aVacancy("concurrent-null-history-" + UUID.randomUUID()).getId();
        when(aiPort.generate(any(), any())).thenReturn(validAiResponse());

        List<PrepareApplicationPackageOutcome> outcomes = runConcurrently(vacancyId);

        verify(aiPort, times(1)).generate(any(), any());
        assertBothOutcomesAreSafeAndAgreeOnOneGeneration(outcomes);
        assertThat(generationRepositoryPort.findByVacancyId(vacancyId)).hasSize(1);
    }

    @Test
    @Order(4)
    void concurrentFirstTimeRequests_withCareerHistoryVersion_atMostOneAiGenerationCallWins() throws Exception {
        ensureCareerHistoryExists();
        CandidateContextSnapshot snapshot = candidateContextProvider.loadCurrentContext();
        assertThat(snapshot.careerHistory()).as("this test requires Career History to exist for the seeded profile").isPresent();

        UUID vacancyId = aVacancy("concurrent-with-history-" + UUID.randomUUID()).getId();
        when(aiPort.generate(any(), any())).thenReturn(validAiResponse());

        List<PrepareApplicationPackageOutcome> outcomes = runConcurrently(vacancyId);

        verify(aiPort, times(1)).generate(any(), any());
        assertBothOutcomesAreSafeAndAgreeOnOneGeneration(outcomes);
        assertThat(generationRepositoryPort.findByVacancyId(vacancyId)).hasSize(1);
    }

    // ==================== Stale IN_PROGRESS recovery (Sprint 10 Step 6) ====================

    @Test
    @Order(2)
    void staleInProgressGeneration_singleRequest_recoversAndProducesCompletedGeneration() {
        // Reads whatever the current effective Career History version actually is, rather than
        // assuming null - this test only cares that a matching stale row gets recovered, not about
        // the null-vs-present distinction itself (see the two concurrent tests below for that).
        CandidateContextSnapshot snapshot = candidateContextProvider.loadCurrentContext();
        UUID vacancyId = aVacancy("stale-recovery-single-" + UUID.randomUUID()).getId();
        UUID staleId = seedStaleInProgressGeneration(vacancyId, snapshot.candidateProfileVersion(),
                snapshot.careerHistory().map(CareerHistoryAggregate::version).orElse(null));
        when(aiPort.generate(any(), any())).thenReturn(validAiResponse());

        PrepareApplicationPackageOutcome outcome = useCase.prepare(vacancyId);

        ApplicationMaterialGeneration recoveredStale = generationRepositoryPort.findById(staleId).orElseThrow();
        assertThat(recoveredStale.status()).isEqualTo(ApplicationMaterialGenerationStatus.FAILED);
        assertThat(recoveredStale.failureCode()).isEqualTo(ApplicationMaterialsGenerationFailureCode.STALE_IN_PROGRESS.name());

        assertThat(outcome).isInstanceOf(PrepareApplicationPackageOutcome.Prepared.class);
        UUID newGenerationId = ((PrepareApplicationPackageOutcome.Prepared) outcome).preparedPackage().generationId();
        assertThat(newGenerationId).isNotEqualTo(staleId);

        List<ApplicationMaterialGeneration> all = generationRepositoryPort.findByVacancyId(vacancyId);
        assertThat(all).hasSize(2);
        verify(aiPort, times(1)).generate(any(), any());
    }

    @Test
    @Order(3)
    void concurrentRequestsAgainstStaleInProgressGeneration_nullCareerHistory_atMostOneAiCallAndOneReplacement() throws Exception {
        CandidateContextSnapshot snapshot = candidateContextProvider.loadCurrentContext();
        assertThat(snapshot.careerHistory()).as("this test requires no Career History for the seeded profile").isEmpty();

        UUID vacancyId = aVacancy("stale-recovery-concurrent-null-" + UUID.randomUUID()).getId();
        UUID staleId = seedStaleInProgressGeneration(vacancyId, snapshot.candidateProfileVersion(), null);
        when(aiPort.generate(any(), any())).thenReturn(validAiResponse());

        List<PrepareApplicationPackageOutcome> outcomes = runConcurrently(vacancyId);

        assertStaleRowRecoveredExactlyOnceWithOneReplacement(vacancyId, staleId);
        verify(aiPort, times(1)).generate(any(), any());
        assertBothOutcomesAreSafeAndAgreeOnOneGeneration(outcomes);
    }

    @Test
    @Order(5)
    void concurrentRequestsAgainstStaleInProgressGeneration_withCareerHistory_atMostOneAiCallAndOneReplacement() throws Exception {
        ensureCareerHistoryExists();
        CandidateContextSnapshot snapshot = candidateContextProvider.loadCurrentContext();
        assertThat(snapshot.careerHistory()).as("this test requires Career History to exist for the seeded profile").isPresent();

        UUID vacancyId = aVacancy("stale-recovery-concurrent-with-history-" + UUID.randomUUID()).getId();
        UUID staleId = seedStaleInProgressGeneration(
                vacancyId, snapshot.candidateProfileVersion(), snapshot.careerHistory().orElseThrow().version());
        when(aiPort.generate(any(), any())).thenReturn(validAiResponse());

        List<PrepareApplicationPackageOutcome> outcomes = runConcurrently(vacancyId);

        assertStaleRowRecoveredExactlyOnceWithOneReplacement(vacancyId, staleId);
        verify(aiPort, times(1)).generate(any(), any());
        assertBothOutcomesAreSafeAndAgreeOnOneGeneration(outcomes);
    }

    private void assertStaleRowRecoveredExactlyOnceWithOneReplacement(UUID vacancyId, UUID staleId) {
        ApplicationMaterialGeneration recoveredStale = generationRepositoryPort.findById(staleId).orElseThrow();
        assertThat(recoveredStale.status()).isEqualTo(ApplicationMaterialGenerationStatus.FAILED);
        assertThat(recoveredStale.failureCode()).isEqualTo(ApplicationMaterialsGenerationFailureCode.STALE_IN_PROGRESS.name());

        // Exactly one logical replacement generation - the abandoned row plus exactly one new one,
        // never two concurrently-created replacements racing each other.
        List<ApplicationMaterialGeneration> all = generationRepositoryPort.findByVacancyId(vacancyId);
        assertThat(all).hasSize(2);
        assertThat(all).filteredOn(g -> !g.id().equals(staleId)).hasSize(1);
    }

    private UUID seedStaleInProgressGeneration(UUID vacancyId, long candidateProfileVersion, Long careerHistoryVersion) {
        // Comfortably past application.yml's real default stale-in-progress-timeout (15m) -
        // deliberately not overridden in this test class, so this proves the actual configured
        // production default, not an artificially shortened test value.
        Instant oldRequestedAt = Instant.now().minus(Duration.ofMinutes(20));
        Instant oldStartedAt = oldRequestedAt.plusSeconds(1);
        ApplicationMaterialGeneration pending = generationRepositoryPort.save(
                ApplicationMaterialGeneration.requestNew(vacancyId, candidateProfileVersion, careerHistoryVersion, oldRequestedAt));
        return generationRepositoryPort.save(pending.start(oldStartedAt)).id();
    }

    // ==================== Assertions ====================

    /**
     * Every safe outcome the "loser" of the race may observe, per the acceptance requirement: no
     * one exact timing-dependent outcome is required, only that both threads got a controlled
     * result (never {@code Failed}/{@code VacancyNotFound}) referencing the exact same generation.
     */
    private void assertBothOutcomesAreSafeAndAgreeOnOneGeneration(List<PrepareApplicationPackageOutcome> outcomes) {
        assertThat(outcomes).hasSize(2);
        List<UUID> generationIds = outcomes.stream().map(this::generationIdOf).distinct().toList();
        assertThat(generationIds).as("both requests must resolve against the exact same generation: %s", outcomes).hasSize(1);
        assertThat(outcomes).as("neither request may fail: %s", outcomes)
                .allSatisfy(outcome -> assertThat(outcome)
                        .isInstanceOfAny(PrepareApplicationPackageOutcome.Prepared.class, PrepareApplicationPackageOutcome.AlreadyInProgress.class));
        assertThat(outcomes).as("at least one request must successfully obtain the prepared package: %s", outcomes)
                .anyMatch(outcome -> outcome instanceof PrepareApplicationPackageOutcome.Prepared);
    }

    private UUID generationIdOf(PrepareApplicationPackageOutcome outcome) {
        return switch (outcome) {
            case PrepareApplicationPackageOutcome.Prepared prepared -> prepared.preparedPackage().generationId();
            case PrepareApplicationPackageOutcome.AlreadyInProgress inProgress -> inProgress.generationId();
            case PrepareApplicationPackageOutcome.VacancyNotFound ignored ->
                    throw new AssertionError("Unexpected VacancyNotFound outcome");
            case PrepareApplicationPackageOutcome.Failed failed -> failed.generationId();
        };
    }

    // ==================== Concurrency harness ====================

    private List<PrepareApplicationPackageOutcome> runConcurrently(UUID vacancyId) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<PrepareApplicationPackageOutcome>> futures = new ArrayList<>();
            futures.add(executor.submit(() -> {
                barrier.await();
                return useCase.prepare(vacancyId);
            }));
            futures.add(executor.submit(() -> {
                barrier.await();
                return useCase.prepare(vacancyId);
            }));
            List<PrepareApplicationPackageOutcome> outcomes = new ArrayList<>();
            for (Future<PrepareApplicationPackageOutcome> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            executor.shutdown();
        }
    }

    // ==================== Fixtures ====================

    private void ensureCareerHistoryExists() {
        UUID candidateProfileId = candidateContextProvider.loadCurrentContext().candidateProfileId();
        if (careerHistoryRepositoryPort.findByCandidateProfileId(candidateProfileId).isPresent()) {
            return;
        }
        CareerCompany company = new CareerCompany(null, "Acme Corp", null, null, null, null, 0, List.of());
        careerHistoryRepositoryPort.save(new CareerHistoryAggregate(null, candidateProfileId, List.of(company), 0L));
    }

    private ApplicationMaterialsGenerationResponse validAiResponse() {
        return new ApplicationMaterialsGenerationResponse(minimalCoverLetter(), "openai", "gpt-4o-mini", 1);
    }

    private GeneratedCoverLetter minimalCoverLetter() {
        return new GeneratedCoverLetter(null, List.of(new GeneratedCoverLetterParagraph("I am excited to apply.", List.of())), "Sincerely");
    }

    private Vacancy aVacancy(String urlSuffix) {
        Company company = companyRepository.save(Company.builder().name("Example Systems " + urlSuffix).build());
        return vacancyRepository.save(Vacancy.builder()
                .company(company)
                .title("Demo Backend Engineer")
                .url("https://example.test/jobs/" + urlSuffix)
                .canonicalUrl("https://example.test/jobs/" + urlSuffix)
                .build());
    }
}
