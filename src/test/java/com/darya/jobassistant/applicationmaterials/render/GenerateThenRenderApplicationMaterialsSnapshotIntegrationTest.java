package com.darya.jobassistant.applicationmaterials.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.AbstractIntegrationTest;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGeneration;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationRepositoryPort;
import com.darya.jobassistant.applicationmaterials.generation.GenerateApplicationMaterialsOutcome;
import com.darya.jobassistant.applicationmaterials.generation.GenerateApplicationMaterialsUseCase;
import com.darya.jobassistant.applicationmaterials.generation.GenerationOutcomeStatus;
import com.darya.jobassistant.applicationmaterials.generation.model.ApplicationMaterialsAiPort;
import com.darya.jobassistant.applicationmaterials.generation.model.ApplicationMaterialsGenerationResponse;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCoverLetter;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCoverLetterParagraph;
import com.darya.jobassistant.applicationmaterials.render.snapshot.aggregate.ApplicationMaterialRenderSnapshot;
import com.darya.jobassistant.applicationmaterials.render.snapshot.aggregate.ApplicationMaterialRenderSnapshotRepositoryPort;
import com.darya.jobassistant.applicationmaterials.result.aggregate.ApplicationMaterialGenerationResultRepositoryPort;
import com.darya.jobassistant.candidatecontext.CandidateContextProvider;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvTailoringResult;
import com.darya.jobassistant.candidatecontext.cv.tailoring.ai.CvTailoringAiPort;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileAggregate;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileRepositoryPort;
import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.repository.CompanyRepository;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Sprint 10 Step 4 (production-readiness fix): proves the eager render-snapshot creation added to
 * {@link GenerateApplicationMaterialsUseCase} - a real end-to-end {@link
 * GenerateApplicationMaterialsUseCase#generate} followed by a real {@link
 * RenderApplicationMaterialsUseCase#render} - a separate class from both {@code
 * GenerateApplicationMaterialsUseCaseIntegrationTest} and {@code
 * RenderApplicationMaterialsUseCaseIntegrationTest} because it is the only place both use cases are
 * exercised together against the same generation.
 */
class GenerateThenRenderApplicationMaterialsSnapshotIntegrationTest extends AbstractIntegrationTest {

    @TempDir
    static Path tempStorageRoot;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("application-materials.storage.local.root-directory", () -> tempStorageRoot.toString());
    }

    @Autowired
    private GenerateApplicationMaterialsUseCase generateUseCase;

    @Autowired
    private RenderApplicationMaterialsUseCase renderUseCase;

    @Autowired
    private ApplicationMaterialGenerationRepositoryPort generationRepositoryPort;

    @Autowired
    private ApplicationMaterialGenerationResultRepositoryPort resultRepositoryPort;

    @Autowired
    private ApplicationMaterialRenderSnapshotRepositoryPort snapshotRepositoryPort;

    @Autowired
    private CandidateProfileRepositoryPort candidateProfileRepositoryPort;

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

    // ==================== Newly-COMPLETED generation atomically has both result and snapshot ====================

    @Test
    void generate_success_atomicallyPersistsRenderSnapshotAlongsideResult() {
        Vacancy vacancy = aVacancy("eager-snapshot-" + UUID.randomUUID());
        UUID generationId = createMatchingGeneration(vacancy.getId());
        when(aiPort.generate(any(), any())).thenReturn(validAiResponse());
        when(cvTailoringAiPort.tailor(any(), any())).thenReturn(new CvTailoringResult(null, List.of(), List.of(), List.of()));

        GenerateApplicationMaterialsOutcome outcome = generateUseCase.generate(generationId);

        assertThat(outcome.status()).isEqualTo(GenerationOutcomeStatus.COMPLETED);
        assertThat(resultRepositoryPort.findByGenerationId(generationId)).isPresent();
        ApplicationMaterialRenderSnapshot snapshot = snapshotRepositoryPort.findByGenerationId(generationId).orElseThrow();
        assertThat(snapshot.schemaVersion()).isEqualTo(ApplicationMaterialRenderSnapshot.CURRENT_SCHEMA_VERSION);
        // The snapshot was assembled from this exact vacancy - proves it used the same JobOffer
        // the AI call itself used, not something independently reloaded afterward.
        assertThat(snapshot.content().coverLetter().vacancyTitle()).isEqualTo(vacancy.getTitle());
    }

    // ==================== Candidate data changing after completion cannot make the result unrenderable ====================

    @Test
    void generate_thenCandidateProfileChanges_renderStillSucceedsUsingTheEagerlyCreatedSnapshot() {
        Vacancy vacancy = aVacancy("eager-then-drift-" + UUID.randomUUID());
        UUID generationId = createMatchingGeneration(vacancy.getId());
        when(aiPort.generate(any(), any())).thenReturn(validAiResponse());
        when(cvTailoringAiPort.tailor(any(), any())).thenReturn(new CvTailoringResult(null, List.of(), List.of(), List.of()));
        generateUseCase.generate(generationId);
        UUID snapshotIdRightAfterGeneration = snapshotRepositoryPort.findByGenerationId(generationId).orElseThrow().id();

        // An unconditional version-checked resave (Sprint 9's own established convention) bumps
        // the Candidate Profile version even though every field is left byte-for-byte identical -
        // simulating the profile drifting after this generation already completed.
        CandidateProfileAggregate current = candidateProfileRepositoryPort.findByProfileKey("primary").orElseThrow();
        candidateProfileRepositoryPort.save(current);

        RenderApplicationMaterialsResult renderResult = renderUseCase.render(generationId);

        assertThat(renderResult.cv().sha256Checksum()).hasSize(64);
        // Rendering reused the snapshot created eagerly at completion time - it never needed to
        // reload/re-validate candidate context, so the version drift above never mattered.
        assertThat(snapshotRepositoryPort.findByGenerationId(generationId).orElseThrow().id()).isEqualTo(snapshotIdRightAfterGeneration);
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
