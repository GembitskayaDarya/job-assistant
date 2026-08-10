package com.darya.jobassistant.applicationmaterials.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.AbstractIntegrationTest;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGeneration;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationRepositoryPort;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationStatus;
import com.darya.jobassistant.applicationmaterials.generation.model.ApplicationMaterialsAiException;
import com.darya.jobassistant.applicationmaterials.generation.model.ApplicationMaterialsAiPort;
import com.darya.jobassistant.applicationmaterials.generation.model.ApplicationMaterialsGenerationFailureCode;
import com.darya.jobassistant.applicationmaterials.generation.model.ApplicationMaterialsGenerationResponse;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedApplicationMaterials;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCoverLetter;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCoverLetterParagraph;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCv;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCvExperience;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedExperienceBullet;
import com.darya.jobassistant.applicationmaterials.result.aggregate.ApplicationMaterialGenerationResult;
import com.darya.jobassistant.applicationmaterials.result.aggregate.ApplicationMaterialGenerationResultRepositoryPort;
import com.darya.jobassistant.candidatecontext.CandidateContextProvider;
import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.repository.CompanyRepository;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Sprint 10 Step 3: proves {@link GenerateApplicationMaterialsUseCase} end to end against real,
 * Spring-wired beans and real PostgreSQL - lifecycle transitions, atomicity, idempotency,
 * concurrency, failure classification, and (mirroring {@code
 * AnalyzeVacancyServiceTransactionBoundaryIntegrationTest}'s established technique exactly) that
 * the AI call never runs inside an open database transaction.
 */
class GenerateApplicationMaterialsUseCaseIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private GenerateApplicationMaterialsUseCase useCase;

    @Autowired
    private ApplicationMaterialGenerationRepositoryPort generationRepositoryPort;

    @Autowired
    private ApplicationMaterialGenerationResultRepositoryPort resultRepositoryPort;

    @Autowired
    private CandidateContextProvider candidateContextProvider;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private VacancyRepository vacancyRepository;

    @MockitoBean
    private ApplicationMaterialsAiPort aiPort;

    // ==================== 1-3. Successful generation, lifecycle, result persisted once ====================

    @Test
    void generate_success_completesLifecycleAndPersistsResultExactlyOnce() {
        UUID vacancyId = aVacancy("success-" + UUID.randomUUID()).getId();
        UUID generationId = createMatchingGeneration(vacancyId);
        when(aiPort.generate(any(), any())).thenReturn(validAiResponse());

        GenerateApplicationMaterialsOutcome outcome = useCase.generate(generationId);

        assertThat(outcome.status()).isEqualTo(GenerationOutcomeStatus.COMPLETED);
        assertThat(outcome.generation().status()).isEqualTo(ApplicationMaterialGenerationStatus.COMPLETED);
        assertThat(outcome.result()).isNotNull();
        assertThat(resultRepositoryPort.findByGenerationId(generationId)).isPresent();
        verify(aiPort, times(1)).generate(any(), any());
    }

    // ==================== 5. Candidate Profile version mismatch ====================

    @Test
    void generate_candidateProfileVersionMismatch_failsSafelyWithoutCallingAiProvider() {
        UUID vacancyId = aVacancy("profile-mismatch-" + UUID.randomUUID()).getId();
        UUID generationId = generationRepositoryPort.save(
                ApplicationMaterialGeneration.requestNew(vacancyId, 999L, null, Instant.now())).id();

        GenerateApplicationMaterialsOutcome outcome = useCase.generate(generationId);

        assertThat(outcome.status()).isEqualTo(GenerationOutcomeStatus.FAILED);
        assertThat(outcome.generation().status()).isEqualTo(ApplicationMaterialGenerationStatus.FAILED);
        assertThat(outcome.generation().failureCode())
                .isEqualTo(ApplicationMaterialsGenerationFailureCode.CANDIDATE_CONTEXT_VERSION_MISMATCH.name());
        verify(aiPort, never()).generate(any(), any());
    }

    // ==================== 6. Career History version mismatch (expected present, actually absent) ====================

    @Test
    void generate_careerHistoryVersionMismatch_failsSafelyWithoutCallingAiProvider() {
        UUID vacancyId = aVacancy("history-mismatch-" + UUID.randomUUID()).getId();
        UUID generationId = generationRepositoryPort.save(
                ApplicationMaterialGeneration.requestNew(vacancyId, 0L, 5L, Instant.now())).id();

        GenerateApplicationMaterialsOutcome outcome = useCase.generate(generationId);

        assertThat(outcome.status()).isEqualTo(GenerationOutcomeStatus.FAILED);
        assertThat(outcome.generation().failureCode())
                .isEqualTo(ApplicationMaterialsGenerationFailureCode.CANDIDATE_CONTEXT_VERSION_MISMATCH.name());
        verify(aiPort, never()).generate(any(), any());
    }

    // ==================== 7. AI provider failure -> safe FAILED state ====================

    @Test
    void generate_aiProviderFailure_transitionsToFailedWithSafeGenericMessage() {
        UUID vacancyId = aVacancy("ai-failure-" + UUID.randomUUID()).getId();
        UUID generationId = createMatchingGeneration(vacancyId);
        when(aiPort.generate(any(), any()))
                .thenThrow(new ApplicationMaterialsAiException("wrapped provider failure", new RuntimeException("HTTP 429 secret-detail")));

        GenerateApplicationMaterialsOutcome outcome = useCase.generate(generationId);

        assertThat(outcome.status()).isEqualTo(GenerationOutcomeStatus.FAILED);
        assertThat(outcome.generation().failureCode()).isEqualTo(ApplicationMaterialsGenerationFailureCode.AI_PROVIDER_ERROR.name());
        assertThat(outcome.generation().failureMessage()).doesNotContain("429").doesNotContain("secret-detail");
        assertThat(resultRepositoryPort.findByGenerationId(generationId)).isEmpty();
    }

    @Test
    void generate_malformedAiResponse_transitionsToFailedWithMalformedResponseCode() {
        UUID vacancyId = aVacancy("malformed-" + UUID.randomUUID()).getId();
        UUID generationId = createMatchingGeneration(vacancyId);
        when(aiPort.generate(any(), any())).thenThrow(new ApplicationMaterialsAiException("AI provider returned an incomplete response"));

        GenerateApplicationMaterialsOutcome outcome = useCase.generate(generationId);

        assertThat(outcome.generation().failureCode()).isEqualTo(ApplicationMaterialsGenerationFailureCode.MALFORMED_AI_RESPONSE.name());
    }

    // ==================== Validation failure ====================

    @Test
    void generate_resultValidationFailure_transitionsToFailedWithValidationFailedCode() {
        UUID vacancyId = aVacancy("validation-failure-" + UUID.randomUUID()).getId();
        UUID generationId = createMatchingGeneration(vacancyId);
        GeneratedCvExperience experienceWithUnknownPosition = new GeneratedCvExperience(
                UUID.randomUUID(), List.of(new GeneratedExperienceBullet("Did work", List.of(UUID.randomUUID()))));
        GeneratedApplicationMaterials invalidMaterials = new GeneratedApplicationMaterials(
                new GeneratedCv("Headline", "Summary", List.of(), List.of(experienceWithUnknownPosition), List.of()),
                minimalCoverLetter());
        when(aiPort.generate(any(), any()))
                .thenReturn(new ApplicationMaterialsGenerationResponse(invalidMaterials, "openai", "gpt-4o-mini", 1));

        GenerateApplicationMaterialsOutcome outcome = useCase.generate(generationId);

        assertThat(outcome.generation().failureCode())
                .isEqualTo(ApplicationMaterialsGenerationFailureCode.RESULT_VALIDATION_FAILED.name());
        assertThat(resultRepositoryPort.findByGenerationId(generationId)).isEmpty();
    }

    // ==================== 14. Duplicate concurrent start ====================

    @Test
    void generate_secondCallAfterAlreadyInProgress_reportsAlreadyInProgressWithoutCallingAiAgain() {
        UUID vacancyId = aVacancy("concurrent-" + UUID.randomUUID()).getId();
        UUID generationId = createMatchingGeneration(vacancyId);
        // Simulate a concurrent winner: directly transition the generation to IN_PROGRESS outside the use case.
        ApplicationMaterialGeneration pending = generationRepositoryPort.findById(generationId).orElseThrow();
        generationRepositoryPort.save(pending.start(Instant.now()));

        GenerateApplicationMaterialsOutcome outcome = useCase.generate(generationId);

        assertThat(outcome.status()).isEqualTo(GenerationOutcomeStatus.ALREADY_IN_PROGRESS);
        verify(aiPort, never()).generate(any(), any());
    }

    // ==================== 15. Already-COMPLETED generation is not regenerated ====================

    @Test
    void generate_alreadyCompletedGeneration_returnsExistingResultWithoutCallingAiAgain() {
        UUID vacancyId = aVacancy("already-completed-" + UUID.randomUUID()).getId();
        UUID generationId = createMatchingGeneration(vacancyId);
        when(aiPort.generate(any(), any())).thenReturn(validAiResponse());
        GenerateApplicationMaterialsOutcome first = useCase.generate(generationId);
        assertThat(first.status()).isEqualTo(GenerationOutcomeStatus.COMPLETED);

        GenerateApplicationMaterialsOutcome second = useCase.generate(generationId);

        assertThat(second.status()).isEqualTo(GenerationOutcomeStatus.ALREADY_COMPLETED);
        assertThat(second.result().id()).isEqualTo(first.result().id());
        verify(aiPort, times(1)).generate(any(), any());
    }

    // ==================== 16. Multiple different generations for one Vacancy remain independent ====================

    @Test
    void generate_multipleGenerationsForOneVacancy_remainIndependent() {
        UUID vacancyId = aVacancy("multi-gen-" + UUID.randomUUID()).getId();
        UUID generationIdA = createMatchingGeneration(vacancyId);
        UUID generationIdB = createMatchingGeneration(vacancyId);
        when(aiPort.generate(any(), any())).thenReturn(validAiResponse());

        GenerateApplicationMaterialsOutcome outcomeA = useCase.generate(generationIdA);
        GenerateApplicationMaterialsOutcome outcomeB = useCase.generate(generationIdB);

        assertThat(outcomeA.status()).isEqualTo(GenerationOutcomeStatus.COMPLETED);
        assertThat(outcomeB.status()).isEqualTo(GenerationOutcomeStatus.COMPLETED);
        assertThat(outcomeA.result().id()).isNotEqualTo(outcomeB.result().id());
        assertThat(resultRepositoryPort.findByGenerationId(generationIdA)).isPresent();
        assertThat(resultRepositoryPort.findByGenerationId(generationIdB)).isPresent();
    }

    // ==================== 20. No transaction active around the AI adapter invocation ====================

    @Test
    void generate_noTransactionIsActiveInsideTheAiPortInvocation() {
        UUID vacancyId = aVacancy("no-tx-" + UUID.randomUUID()).getId();
        UUID generationId = createMatchingGeneration(vacancyId);
        AtomicBoolean transactionActiveDuringAiCall = new AtomicBoolean(true);
        AtomicInteger aiCallCount = new AtomicInteger(0);
        when(aiPort.generate(any(), any())).thenAnswer(invocation -> {
            aiCallCount.incrementAndGet();
            transactionActiveDuringAiCall.set(TransactionSynchronizationManager.isActualTransactionActive());
            return validAiResponse();
        });

        GenerateApplicationMaterialsOutcome outcome = useCase.generate(generationId);

        assertThat(outcome.status()).isEqualTo(GenerationOutcomeStatus.COMPLETED);
        assertThat(aiCallCount.get()).isEqualTo(1);
        assertThat(transactionActiveDuringAiCall.get())
                .as("no database transaction must be active while ApplicationMaterialsAiPort.generate runs")
                .isFalse();
    }

    // ==================== 4. Result persistence + COMPLETED transition atomicity ====================

    /**
     * Simulates a concurrent completion of the same generation happening between {@code
     * GenerateApplicationMaterialsUseCase}'s own {@code start()} and its later {@code complete()} -
     * triggered deterministically from inside the mocked AI call (which the use case invokes with
     * no transaction open, so a direct write from here is safe and immediately committed). The use
     * case's own completion transaction then attempts, in order, to save a result and complete the
     * generation using its now-stale in-memory version; the second write loses the optimistic-
     * version race, and the whole transaction - including the result insert that ran first inside
     * it - must roll back together. Proven by asserting no result row was left behind afterward.
     */
    @Test
    void generate_whenCompletionRacesLost_rollsBackTheResultInsertTogetherWithTheFailedGenerationUpdate() {
        UUID vacancyId = aVacancy("atomicity-" + UUID.randomUUID()).getId();
        UUID generationId = createMatchingGeneration(vacancyId);
        when(aiPort.generate(any(), any())).thenAnswer(invocation -> {
            ApplicationMaterialGeneration current = generationRepositoryPort.findById(generationId).orElseThrow();
            generationRepositoryPort.save(current.complete(Instant.now()));
            return validAiResponse();
        });

        try {
            useCase.generate(generationId);
        } catch (RuntimeException e) {
            // Either a FAILED outcome or a propagated exception (if the subsequent FAILED
            // transition also lost its own race) are both acceptable here - see class javadoc.
        }

        assertThat(resultRepositoryPort.findByGenerationId(generationId)).isEmpty();
    }

    // ==================== Helpers ====================

    private UUID createMatchingGeneration(UUID vacancyId) {
        Long candidateProfileVersion = candidateContextProvider.loadCurrentContext().candidateProfileVersion();
        return generationRepositoryPort.save(
                ApplicationMaterialGeneration.requestNew(vacancyId, candidateProfileVersion, null, Instant.now())).id();
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
}
