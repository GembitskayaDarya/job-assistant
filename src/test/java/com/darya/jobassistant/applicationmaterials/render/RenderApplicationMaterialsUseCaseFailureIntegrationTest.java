package com.darya.jobassistant.applicationmaterials.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.AbstractIntegrationTest;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGeneration;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationRepositoryPort;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationStatus;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCoverLetter;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCoverLetterParagraph;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCv;
import com.darya.jobassistant.applicationmaterials.render.model.ApplicationMaterialDocumentRendererPort;
import com.darya.jobassistant.applicationmaterials.render.model.DocumentRenderingException;
import com.darya.jobassistant.applicationmaterials.result.aggregate.ApplicationMaterialGenerationResult;
import com.darya.jobassistant.applicationmaterials.result.aggregate.ApplicationMaterialGenerationResultRepositoryPort;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileRepositoryPort;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryRepositoryPort;
import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.repository.CompanyRepository;
import com.darya.jobassistant.integrations.filestorage.FileStorageException;
import com.darya.jobassistant.integrations.filestorage.FileStoragePort;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Sprint 10 Step 4: proves that a rendering/storage failure never changes {@link
 * ApplicationMaterialGeneration#status()} away from {@link ApplicationMaterialGenerationStatus#COMPLETED} -
 * rendering is a downstream, independently retryable concern from successful AI generation. A
 * separate test class from {@link RenderApplicationMaterialsUseCaseIntegrationTest}: {@code
 * @MockitoBean} replaces a port for this class's entire Spring context, which would break that
 * class's real-rendering/real-storage assertions if combined here.
 */
class RenderApplicationMaterialsUseCaseFailureIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RenderApplicationMaterialsUseCase useCase;

    @Autowired
    private ApplicationMaterialGenerationRepositoryPort generationRepositoryPort;

    @Autowired
    private ApplicationMaterialGenerationResultRepositoryPort resultRepositoryPort;

    @Autowired
    private CandidateProfileRepositoryPort candidateProfileRepositoryPort;

    @Autowired
    private CareerHistoryRepositoryPort careerHistoryRepositoryPort;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private VacancyRepository vacancyRepository;

    @MockitoBean
    private ApplicationMaterialDocumentRendererPort rendererPort;

    @MockitoBean
    private FileStoragePort fileStoragePort;

    private static final Instant REQUESTED_AT = Instant.parse("2026-01-01T00:00:00Z");

    // ==================== 26. Generation remains COMPLETED when rendering fails ====================

    @Test
    void render_rendererFailure_keepsGenerationCompletedAndThrowsRenderingFailed() {
        when(rendererPort.renderCv(any())).thenThrow(new DocumentRenderingException("PDF layout failed"));
        UUID generationId = completedGenerationWithEmptyResult();

        assertThatThrownBy(() -> useCase.render(generationId))
                .isInstanceOf(RenderApplicationMaterialsException.class)
                .extracting(e -> ((RenderApplicationMaterialsException) e).reason())
                .isEqualTo(RenderApplicationMaterialsException.Reason.RENDERING_FAILED);

        ApplicationMaterialGeneration afterFailure = generationRepositoryPort.findById(generationId).orElseThrow();
        assertThat(afterFailure.status()).isEqualTo(ApplicationMaterialGenerationStatus.COMPLETED);
    }

    // ==================== Generation remains COMPLETED when storage fails ====================

    @Test
    void render_storageFailure_keepsGenerationCompletedAndThrowsStorageFailed() {
        when(rendererPort.renderCv(any())).thenReturn(
                new com.darya.jobassistant.applicationmaterials.render.model.RenderedDocument(new byte[]{1, 2, 3}, "application/pdf"));
        when(fileStoragePort.store(any(), any(), any())).thenThrow(new FileStorageException("disk full"));
        UUID generationId = completedGenerationWithEmptyResult();

        assertThatThrownBy(() -> useCase.render(generationId))
                .isInstanceOf(RenderApplicationMaterialsException.class)
                .extracting(e -> ((RenderApplicationMaterialsException) e).reason())
                .isEqualTo(RenderApplicationMaterialsException.Reason.STORAGE_FAILED);

        ApplicationMaterialGeneration afterFailure = generationRepositoryPort.findById(generationId).orElseThrow();
        assertThat(afterFailure.status()).isEqualTo(ApplicationMaterialGenerationStatus.COMPLETED);
    }

    // ==================== Helpers ====================

    private UUID completedGenerationWithEmptyResult() {
        Vacancy vacancy = aVacancy("render-failure-" + UUID.randomUUID());
        var profile = candidateProfileRepositoryPort.findByProfileKey("primary").orElseThrow();
        Long careerHistoryVersion = careerHistoryRepositoryPort.findByCandidateProfileId(profile.id())
                .map(CareerHistoryAggregate::version)
                .orElse(null);
        ApplicationMaterialGeneration pending = generationRepositoryPort.save(
                ApplicationMaterialGeneration.requestNew(vacancy.getId(), profile.version(), careerHistoryVersion, REQUESTED_AT));
        ApplicationMaterialGeneration started = generationRepositoryPort.save(pending.start(Instant.now()));
        ApplicationMaterialGeneration completed = generationRepositoryPort.save(started.complete(Instant.now()));

        GeneratedCv cv = new GeneratedCv("Senior Java Backend Engineer", "Experienced backend engineer.", List.of(), List.of(), List.of());
        GeneratedCoverLetter coverLetter = new GeneratedCoverLetter(
                null, List.of(new GeneratedCoverLetterParagraph("I am excited to apply.", List.of())), "Sincerely");
        resultRepositoryPort.save(ApplicationMaterialGenerationResult.create(
                completed.id(), cv, coverLetter, "openai", "gpt-4o-mini", 1, Instant.now()));
        return completed.id();
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
