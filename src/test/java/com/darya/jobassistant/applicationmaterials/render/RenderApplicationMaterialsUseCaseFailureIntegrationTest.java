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
import com.darya.jobassistant.applicationmaterials.render.ats.AtsPdfTextExtractorPort;
import com.darya.jobassistant.applicationmaterials.render.model.ApplicationMaterialDocumentRendererPort;
import com.darya.jobassistant.applicationmaterials.render.model.DocumentRenderingException;
import com.darya.jobassistant.applicationmaterials.result.aggregate.ApplicationMaterialGenerationResult;
import com.darya.jobassistant.applicationmaterials.result.aggregate.ApplicationMaterialGenerationResultRepositoryPort;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvDocument;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvHeader;
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

    @MockitoBean
    private AtsPdfTextExtractorPort atsTextExtractorPort;

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

    // ==================== Generation remains COMPLETED when ATS verification fails (Part 4/6) ====================

    /**
     * Mirrors the real production incident: AI content generation succeeds (generation reaches
     * {@code COMPLETED}), but the rendered CV fails {@link
     * com.darya.jobassistant.applicationmaterials.render.ats.AtsCvVerifier} structural verification.
     * The generation's own status must stay {@code COMPLETED} - rendering/ATS validation is a
     * downstream, independently retryable concern, never something that unwinds successful AI
     * generation - and no CV artifact may be persisted for content that failed the gate.
     */
    @Test
    void render_atsVerificationFailure_keepsGenerationCompletedAndThrowsAtsVerificationFailed() {
        when(rendererPort.renderCv(any())).thenReturn(
                new com.darya.jobassistant.applicationmaterials.render.model.RenderedDocument(new byte[]{1, 2, 3}, "application/pdf"));
        // Extracted text missing the header's full name - AtsCvVerifier.MISSING_FULL_NAME.
        when(atsTextExtractorPort.extractText(any())).thenReturn("some unrelated extracted text");
        UUID generationId = completedGenerationWithFullNameOnlyResult();

        assertThatThrownBy(() -> useCase.render(generationId))
                .isInstanceOf(RenderApplicationMaterialsException.class)
                .extracting(e -> ((RenderApplicationMaterialsException) e).reason())
                .isEqualTo(RenderApplicationMaterialsException.Reason.ATS_VERIFICATION_FAILED);

        ApplicationMaterialGeneration afterFailure = generationRepositoryPort.findById(generationId).orElseThrow();
        assertThat(afterFailure.status()).isEqualTo(ApplicationMaterialGenerationStatus.COMPLETED);
    }

    /**
     * Part 6.D / Part 4: once the underlying cause of an ATS failure is fixed (here simulated by the
     * text extractor simply returning correct text on the next call, standing in for "a renderer bug
     * was fixed"), a normal retry of {@link RenderApplicationMaterialsUseCase#render} against the
     * SAME {@code COMPLETED} generation - no new AI call, no new generation row, no manual DB
     * patching - reaches a fully stored, valid package. Proves rendering is genuinely idempotent/
     * retryable rather than permanently poisoned by one earlier failure.
     */
    @Test
    void render_afterAtsVerificationFailure_subsequentRetrySucceedsAndStoresArtifacts() {
        when(rendererPort.renderCv(any())).thenReturn(
                new com.darya.jobassistant.applicationmaterials.render.model.RenderedDocument(new byte[]{1, 2, 3}, "application/pdf"));
        when(rendererPort.renderCoverLetter(any())).thenReturn(
                new com.darya.jobassistant.applicationmaterials.render.model.RenderedDocument(new byte[]{4, 5, 6}, "application/pdf"));
        when(fileStoragePort.store(any(), any(), any())).thenAnswer(invocation -> new com.darya.jobassistant.integrations.filestorage.StoredFile(
                invocation.getArgument(0), 3, "a".repeat(64), false));
        UUID generationId = completedGenerationWithFullNameOnlyResult();

        when(atsTextExtractorPort.extractText(any())).thenReturn("missing the name entirely");
        assertThatThrownBy(() -> useCase.render(generationId))
                .isInstanceOf(RenderApplicationMaterialsException.class);
        ApplicationMaterialGeneration stillCompleted = generationRepositoryPort.findById(generationId).orElseThrow();
        assertThat(stillCompleted.status()).isEqualTo(ApplicationMaterialGenerationStatus.COMPLETED);

        // Simulates the underlying rendering defect having been fixed: the extracted text now
        // actually contains what AtsCvVerifier requires - the real renderer draws the full name
        // uppercase (see AtsCvVerifier#toUppercaseHeading's javadoc), so the fixed extracted text
        // must match that, not the header's raw-case value.
        when(atsTextExtractorPort.extractText(any())).thenReturn("JANE CANDIDATE");

        RenderApplicationMaterialsResult result = useCase.render(generationId);

        assertThat(result.cv()).isNotNull();
        assertThat(result.coverLetter()).isNotNull();
        assertThat(generationRepositoryPort.findById(generationId).orElseThrow().status())
                .isEqualTo(ApplicationMaterialGenerationStatus.COMPLETED);
    }

    // ==================== Generation remains COMPLETED when storage fails ====================

    @Test
    void render_storageFailure_keepsGenerationCompletedAndThrowsStorageFailed() {
        when(rendererPort.renderCv(any())).thenReturn(
                new com.darya.jobassistant.applicationmaterials.render.model.RenderedDocument(new byte[]{1, 2, 3}, "application/pdf"));
        // Empty TailoredCvDocument fixture (see completedGenerationWithEmptyResult) has nothing for
        // AtsCvVerifier to require, so any non-blank extracted text passes ATS verification -
        // isolating this test to the storage-failure path it actually targets.
        when(atsTextExtractorPort.extractText(any())).thenReturn("stub extracted text");
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

        TailoredCvDocument cv = new TailoredCvDocument(
                new TailoredCvHeader(null, null, null, null, null, null), null, List.of(), List.of(), List.of(), List.of(), List.of());
        GeneratedCoverLetter coverLetter = new GeneratedCoverLetter(
                null, List.of(new GeneratedCoverLetterParagraph("I am excited to apply.", List.of())), "Sincerely");
        resultRepositoryPort.save(ApplicationMaterialGenerationResult.create(
                completed.id(), cv, coverLetter, "openai", "gpt-4o-mini", 1, Instant.now()));
        return completed.id();
    }

    /**
     * A generation whose {@code TailoredCvDocument} has exactly one AtsCvVerifier-checked fact - the
     * header's full name - so a test can deterministically control whether ATS verification passes or
     * fails purely via what the (mocked) extracted text contains.
     */
    private UUID completedGenerationWithFullNameOnlyResult() {
        Vacancy vacancy = aVacancy("ats-failure-" + UUID.randomUUID());
        var profile = candidateProfileRepositoryPort.findByProfileKey("primary").orElseThrow();
        Long careerHistoryVersion = careerHistoryRepositoryPort.findByCandidateProfileId(profile.id())
                .map(CareerHistoryAggregate::version)
                .orElse(null);
        ApplicationMaterialGeneration pending = generationRepositoryPort.save(
                ApplicationMaterialGeneration.requestNew(vacancy.getId(), profile.version(), careerHistoryVersion, REQUESTED_AT));
        ApplicationMaterialGeneration started = generationRepositoryPort.save(pending.start(Instant.now()));
        ApplicationMaterialGeneration completed = generationRepositoryPort.save(started.complete(Instant.now()));

        TailoredCvDocument cv = new TailoredCvDocument(
                new TailoredCvHeader("Jane Candidate", null, null, null, null, null), null, List.of(), List.of(), List.of(), List.of(), List.of());
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
