package com.darya.jobassistant.applicationmaterials.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import com.darya.jobassistant.applicationmaterials.render.snapshot.aggregate.ApplicationMaterialRenderSnapshotRepositoryPort;
import com.darya.jobassistant.applicationmaterials.result.aggregate.ApplicationMaterialGenerationResultRepositoryPort;
import com.darya.jobassistant.candidatecontext.CandidateContextProvider;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvSkillTailoringResult;
import com.darya.jobassistant.candidatecontext.cv.tailoring.ai.CvTailoringAiPort;
import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.repository.CompanyRepository;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Sprint 10 Step 4 (production-readiness fix): proves that a failure persisting the eagerly-created
 * {@code ApplicationMaterialRenderSnapshot} rolls back the whole completion transaction - the
 * semantic result insert and the {@code IN_PROGRESS -> COMPLETED} transition never commit without
 * it. A separate test class from {@link GenerateApplicationMaterialsUseCaseIntegrationTest}: {@code
 * @MockitoBean} replaces {@link ApplicationMaterialRenderSnapshotRepositoryPort} for this class's
 * entire Spring context, which would break that class's real-persistence assertions if combined
 * here.
 */
class GenerateApplicationMaterialsUseCaseSnapshotFailureIntegrationTest extends AbstractIntegrationTest {

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

    @MockitoBean
    private CvTailoringAiPort cvTailoringAiPort;

    @MockitoBean
    private ApplicationMaterialRenderSnapshotRepositoryPort snapshotRepositoryPort;

    @Test
    void generate_whenSnapshotPersistenceFails_rollsBackResultAndCompletionTransition() {
        Vacancy vacancy = aVacancy("snapshot-failure-" + UUID.randomUUID());
        UUID generationId = createMatchingGeneration(vacancy.getId());
        when(aiPort.generate(any(), any())).thenReturn(validAiResponse());
        when(cvTailoringAiPort.tailorSkills(any(), any())).thenReturn(new CvSkillTailoringResult(List.of()));
        when(snapshotRepositoryPort.save(any())).thenThrow(new RuntimeException("simulated snapshot persistence failure"));

        GenerateApplicationMaterialsOutcome outcome = useCase.generate(generationId);

        assertThat(outcome.status()).isEqualTo(GenerationOutcomeStatus.FAILED);
        assertThat(outcome.generation().failureCode()).isEqualTo(ApplicationMaterialsGenerationFailureCode.PERSISTENCE_FAILURE.name());

        ApplicationMaterialGeneration persisted = generationRepositoryPort.findById(generationId).orElseThrow();
        assertThat(persisted.status()).isEqualTo(ApplicationMaterialGenerationStatus.FAILED);
        assertThat(resultRepositoryPort.findByGenerationId(generationId))
                .as("the semantic result insert must roll back together with the failed snapshot insert")
                .isEmpty();
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
        return new ApplicationMaterialsGenerationResponse(minimalCoverLetter(), "openai", "gpt-4o-mini", 1);
    }

    private GeneratedCoverLetter minimalCoverLetter() {
        return new GeneratedCoverLetter(null, List.of(new GeneratedCoverLetterParagraph("I am excited to apply.", List.of())), "Sincerely");
    }
}
