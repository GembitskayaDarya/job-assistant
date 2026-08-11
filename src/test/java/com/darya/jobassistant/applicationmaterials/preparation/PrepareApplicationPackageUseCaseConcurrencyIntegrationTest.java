package com.darya.jobassistant.applicationmaterials.preparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.AbstractIntegrationTest;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationRepositoryPort;
import com.darya.jobassistant.applicationmaterials.generation.model.ApplicationMaterialsAiPort;
import com.darya.jobassistant.applicationmaterials.generation.model.ApplicationMaterialsGenerationResponse;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedApplicationMaterials;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCoverLetter;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCoverLetterParagraph;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCv;
import com.darya.jobassistant.candidatecontext.CandidateContextProvider;
import com.darya.jobassistant.candidatecontext.CandidateContextSnapshot;
import com.darya.jobassistant.careerhistory.aggregate.CareerCompany;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryRepositoryPort;
import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.repository.CompanyRepository;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
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

    @Test
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
        return new ApplicationMaterialsGenerationResponse(minimalMaterials(), "openai", "gpt-4o-mini", 1);
    }

    private GeneratedApplicationMaterials minimalMaterials() {
        return new GeneratedApplicationMaterials(minimalCv(), minimalCoverLetter());
    }

    private GeneratedCv minimalCv() {
        return new GeneratedCv("Senior Backend Engineer", "Experienced backend engineer.", List.of(), List.of(), List.of());
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
